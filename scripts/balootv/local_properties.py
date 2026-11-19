"""Genera local.properties a partir de %USERPROFILE%\\.balootv\\secrets.properties.

Las claves nunca entran en Git: local.properties está en .gitignore y este script solo copia valores.
Uso: python -I scripts/balootv/local_properties.py
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SECRETS = os.path.join(os.path.expanduser("~"), ".balootv", "secrets.properties")
SDK = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")

# Claves de secrets.properties que pasan tal cual a local.properties.
COPIED = [
    "TMDB_API_KEY",
    "TMDB_API_KEY_PUBLICA",  # la del proyecto, para la edición pública (TMDB_API_KEY es la de casa)
    "TORBOX_API_KEY",
    "TRAKT_CLIENT_ID",
    "TRAKT_CLIENT_ID_PUBLICA",  # la app de Trakt del proyecto para la pública; sin ella, la pública usa TRAKT_CLIENT_ID
    "TRAKT_CLIENT_SECRET",
    "BALOOTV_RELEASE_STORE_FILE",
    "BALOOTV_RELEASE_KEY_ALIAS",
    "BALOOTV_RELEASE_STORE_PASSWORD",
    "BALOOTV_RELEASE_KEY_PASSWORD",
]

# Ajustes fijos de compilación (no secretos).
FIXED = {
    "sdk.dir": SDK,
    # La app de Trakt es de tipo Development (solo tu cuenta): exige redirección a localhost. La tele se
    # vincula con código (device flow), así que solo se usa al renovar la sesión y debe coincidir.
    "TRAKT_REDIRECT_URI": "http://localhost",
    # Dolby Vision perfil 7 -> 8.1 (la tele decodifica DV 5/8 pero no 7).
    "DOVI_NATIVE_ENABLED": "true",
    "DOVI_EXTRACTOR_HOOK_READY": "true",
    "DOVI_ENABLE_REAL_LINK": "true",
    "DOVI_LIBDOVI_PREBUILT_ROOT": "DV7/libdovi",
    "USE_LOCAL_FFMPEG_DECODER": "false",
    # Tiempos de intro, resumen y créditos por IMDb/temporada/episodio (lectura pública, sin clave). Sin esto,
    # «Saltar intro» solo funcionaba en anime (AniSkip).
    "INTRODB_API_URL": "https://api.introdb.app/",
}


def read_props(path):
    props = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    return props


def escape(value):
    return value.replace("\\", "\\\\").replace(":", "\\:")


def main():
    if not os.path.exists(SECRETS):
        sys.exit(f"No existe {SECRETS}")
    secrets = read_props(SECRETS)
    lines = ["# Generado por scripts/balootv/local_properties.py. No editar ni subir a Git."]
    lines += [f"{k}={escape(v)}" for k, v in FIXED.items()]
    lines += [f"{k}={escape(secrets[k])}" for k in COPIED if k in secrets]
    out = os.path.join(ROOT, "local.properties")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    missing = [k for k in COPIED if k not in secrets]
    print(f"local.properties escrito ({len(lines) - 1} claves). Sin valor: {', '.join(missing) or 'ninguna'}")


if __name__ == "__main__":
    main()
