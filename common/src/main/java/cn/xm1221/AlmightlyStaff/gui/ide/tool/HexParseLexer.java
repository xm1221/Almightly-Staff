package cn.xm1221.AlmightlyStaff.gui.ide.tool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HexParse 轻量词法分析与括号校验器（纯 Java，不依赖 MC 类）。
 */
public final class HexParseLexer {
    public enum TokenType {
        COMMENT,
        BRACE_OPEN,
        BRACE_CLOSE,
        ESCAPE,
        NUMBER,
        VECTOR,
        PATTERN_KNOWN,
        IDENTIFIER,
        PUNCTUATION,
        WHITESPACE
    }

    public record Token(TokenType type, int start, int end, String text) {
    }

    public record BracketReport(boolean balanced, int openCount, int closeCount, int errorLine, String errorMessage) {
    }

    private HexParseLexer() {
    }

    public static List<Token> tokenizeLine(String line, Set<String> knownIdentifiers) {
        List<Token> out = new ArrayList<>();
        if (line == null || line.isEmpty()) return out;
        int len = line.length();
        int i = 0;
        while (i < len) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                int s = i;
                while (i < len && Character.isWhitespace(line.charAt(i))) i++;
                out.add(new Token(TokenType.WHITESPACE, s, i, line.substring(s, i)));
                continue;
            }
            if (c == '/' && i + 1 < len && line.charAt(i + 1) == '/') {
                out.add(new Token(TokenType.COMMENT, i, len, line.substring(i)));
                break;
            }
            if (c == '/' && i + 1 < len && line.charAt(i + 1) == '*') {
                int end = line.indexOf("*/", i + 2);
                int stop = end >= 0 ? end + 2 : len;
                out.add(new Token(TokenType.COMMENT, i, stop, line.substring(i, stop)));
                i = stop;
                continue;
            }
            if (c == '{') {
                out.add(new Token(TokenType.BRACE_OPEN, i, i + 1, "{"));
                i++;
                continue;
            }
            if (c == '}') {
                out.add(new Token(TokenType.BRACE_CLOSE, i, i + 1, "}"));
                i++;
                continue;
            }
            if (c == '\\') {
                out.add(new Token(TokenType.ESCAPE, i, i + 1, "\\"));
                i++;
                continue;
            }
            if (c == '(') {
                int close = line.indexOf(')', i + 1);
                if (close > i) {
                    String inner = line.substring(i, close + 1);
                    if (isVectorLiteral(inner)) {
                        out.add(new Token(TokenType.VECTOR, i, close + 1, inner));
                        i = close + 1;
                        continue;
                    }
                }
            }
            if (c == ',' || c == ';' || c == '[' || c == ']' || c == '(' || c == ')') {
                out.add(new Token(TokenType.PUNCTUATION, i, i + 1, String.valueOf(c)));
                i++;
                continue;
            }
            // 读取连续标识符或数字（以空白、逗号、括号、分号或注释边界结束，保留路径中的 '/'）
            int s = i;
            while (i < len) {
                char ch = line.charAt(i);
                if (Character.isWhitespace(ch) || ch == ',' || ch == ';' || ch == '{' || ch == '}'
                    || ch == '[' || ch == ']' || ch == '(' || ch == ')' || ch == '\\'
                    || (ch == '/' && i + 1 < len && (line.charAt(i + 1) == '/' || line.charAt(i + 1) == '*'))) {
                    break;
                }
                i++;
            }
            String word = line.substring(s, i);
            TokenType type = classifyWord(word, knownIdentifiers);
            out.add(new Token(type, s, i, word));
        }
        return out;
    }

    private static TokenType classifyWord(String word, Set<String> knownIdentifiers) {
        if (isNumberLiteral(word)) {
            return TokenType.NUMBER;
        }
        if (word.startsWith("num:") || word.startsWith("number:")) {
            return TokenType.NUMBER;
        }
        if (word.startsWith("mask_") && word.length() > 5) {
            return TokenType.PATTERN_KNOWN;
        }
        if (knownIdentifiers != null && !knownIdentifiers.isEmpty()) {
            String lower = word.toLowerCase(Locale.ROOT);
            if (knownIdentifiers.contains(lower)) {
                return TokenType.PATTERN_KNOWN;
            }
            int colon = lower.indexOf(':');
            if (colon < 0 && knownIdentifiers.contains("hexcasting:" + lower)) {
                return TokenType.PATTERN_KNOWN;
            }
            if (colon >= 0 && knownIdentifiers.contains(lower.substring(colon + 1))) {
                return TokenType.PATTERN_KNOWN;
            }
        }
        return TokenType.IDENTIFIER;
    }

    private static boolean isNumberLiteral(String s) {
        if (s == null || s.isEmpty()) return false;
        int i = (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
        if (i >= s.length()) return false;
        boolean sawDigit = false;
        boolean sawDot = false;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sawDigit = true;
            } else if (c == '.' && !sawDot) {
                sawDot = true;
            } else {
                return false;
            }
        }
        return sawDigit;
    }

    private static boolean isVectorLiteral(String s) {
        if (s.length() < 5 || s.charAt(0) != '(' || s.charAt(s.length() - 1) != ')') return false;
        String[] parts = s.substring(1, s.length() - 1).split(",");
        if (parts.length != 3) return false;
        for (String p : parts) {
            if (!isNumberLiteral(p.trim())) return false;
        }
        return true;
    }

    public static BracketReport checkBrackets(String fullText) {
        if (fullText == null || fullText.isEmpty()) {
            return new BracketReport(true, 0, 0, -1, "");
        }
        Deque<Integer> openLines = new ArrayDeque<>();
        int openCount = 0;
        int closeCount = 0;
        int line = 1;
        boolean inComment = false;
        for (int i = 0; i < fullText.length(); i++) {
            char c = fullText.charAt(i);
            if (c == '\n') {
                line++;
                inComment = false;
                continue;
            }
            if (inComment) continue;
            if (c == '/' && i + 1 < fullText.length() && fullText.charAt(i + 1) == '/') {
                inComment = true;
                continue;
            }
            if (c == '{') {
                openCount++;
                openLines.push(line);
            } else if (c == '}') {
                closeCount++;
                if (openLines.isEmpty()) {
                    return new BracketReport(false, openCount, closeCount, line,
                        "Unmatched '}' at line " + line);
                }
                openLines.pop();
            }
        }
        if (!openLines.isEmpty()) {
            int errLine = openLines.peekLast();
            return new BracketReport(false, openCount, closeCount, errLine,
                "Unclosed '{' at line " + errLine);
        }
        return new BracketReport(true, openCount, closeCount, -1, "");
    }

    /** 查找光标相邻括号（cursor 或 cursor-1）的配对括号字符索引，未命中返回 -1。 */
    public static int findMatchingBrace(String text, int cursor) {
        if (text == null || text.isEmpty()) return -1;
        int idx = -1;
        if (cursor >= 0 && cursor < text.length() && (text.charAt(cursor) == '{' || text.charAt(cursor) == '}')) {
            idx = cursor;
        } else if (cursor - 1 >= 0 && cursor - 1 < text.length()
            && (text.charAt(cursor - 1) == '{' || text.charAt(cursor - 1) == '}')) {
            idx = cursor - 1;
        }
        if (idx < 0) return -1;
        char target = text.charAt(idx);
        int depth = 0;
        if (target == '{') {
            for (int i = idx; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        } else {
            for (int i = idx; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == '}') depth++;
                else if (c == '{') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }
}
