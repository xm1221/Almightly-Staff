from importlib.resources import Package

from hexdoc.plugin import (
    HookReturn,
    ModPlugin,
    ModPluginImpl,
    ModPluginWithBook,
    hookimpl,
)
from typing_extensions import override

import hexdoc_almightly_staff

# 静态版本（不依赖 hatch-gradle-version / __gradle_version__.py，该文件被 .gitignore 忽略）
MOD_ID = "almightly_staff"
MOD_VERSION = "0.0.5.21"
FULL_VERSION = "0.0.5.21.1.0"
PY_VERSION = "1.0"


class Almightly_staffPlugin(ModPluginImpl):
    @staticmethod
    @hookimpl
    def hexdoc_mod_plugin(branch: str) -> ModPlugin:
        return Almightly_staffModPlugin(branch=branch)


class Almightly_staffModPlugin(ModPluginWithBook):
    @property
    @override
    def modid(self) -> str:
        return MOD_ID

    @property
    @override
    def full_version(self) -> str:
        return FULL_VERSION

    @property
    @override
    def mod_version(self) -> str:
        return MOD_VERSION

    @property
    @override
    def plugin_version(self) -> str:
        return PY_VERSION

    @override
    def resource_dirs(self) -> HookReturn[Package]:
        # lazy import because generated may not exist when this file is loaded
        # eg. when generating the contents of generated
        # so we only want to import it if we actually need it
        from ._export import generated

        return generated

    @override
    def jinja_template_root(self) -> tuple[Package, str]:
        return hexdoc_almightly_staff, "_templates"
