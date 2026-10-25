#!/usr/bin/env bash
# Publica uma versão nova do GoatLauncher nas releases deste repositório (como o GoatTV).
#   scripts/release.sh 0.2.0 "O que mudou"
# Precisa de: keystore.properties (a chave de assinatura, fora do git) e `gh` com a conta iamSlokker.
set -euo pipefail

VERSION="${1:?Uso: scripts/release.sh <versão> [notas]}"
NOTES="${2:-}"
RELEASES_REPO="GOAThings/GoatLauncher"
# O repositório antigo das versões: os launchers até à 0.8.7 procuram atualizações lá (e o código antigo do
# Downloader, 3400411, aponta para lá). Enquanto houver TVs com essas versões, cada versão sai também lá.
OLD_RELEASES_REPO="GOAThings/GoatLauncher-Releases"
cd "$(dirname "$0")/.."

if [ -n "$(git status --porcelain)" ]; then
    echo "Há alterações por gravar: faz commit primeiro." >&2
    exit 1
fi
# Sem a chave, o build de release sai assinado com a chave de debug: não serve para publicar.
[ -f keystore.properties ] || { echo "Falta o keystore.properties (a chave de assinatura)." >&2; exit 1; }

# versionCode sobe sempre 1; versionName é o que se pediu.
code=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
sed -i "s/versionCode = $code/versionCode = $((code + 1))/; s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" app/build.gradle.kts

./gradlew -q :app:assembleRelease

apk="build/goatlauncher-$VERSION.apk"
mkdir -p build
# O mapeamento do R8 desta versão (só no PC): sem ele, os crashes das TVs vêm com nomes ofuscados (ex.: MainActivity.p).
cp app/build/outputs/mapping/release/mapping.txt "build/mapping-$VERSION.txt"
cp app/build/outputs/apk/release/app-release.apk "$apk"
# Cópia com nome fixo: https://github.com/<repositório>/releases/latest/download/goatlauncher.apk dá sempre a
# versão mais recente (o código do Downloader, 2462776, aponta para a deste repositório).
latest="build/goatlauncher.apk"
cp "$apk" "$latest"

git commit -qam "Versão $VERSION"
git tag -a "v$VERSION" -m "Versão $VERSION"  # anotada: o --follow-tags só envia tags anotadas
git push -q --follow-tags

gh release create "v$VERSION" "$apk" "$latest" --repo "$RELEASES_REPO" --title "GoatLauncher $VERSION" --notes "${NOTES:-Versão $VERSION}"
gh release create "v$VERSION" "$apk" "$latest" --repo "$OLD_RELEASES_REPO" --title "GoatLauncher $VERSION" \
    --notes "${NOTES:-Versão $VERSION}"$'\n\n'"GoatLauncher now lives at https://github.com/$RELEASES_REPO"
echo "Publicada a $VERSION em https://github.com/$RELEASES_REPO/releases"
