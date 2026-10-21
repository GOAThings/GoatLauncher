#!/usr/bin/env bash
# Publica uma versão nova do GoatLauncher no repositório público de versões (o código é privado).
#   scripts/release.sh 0.2.0 "O que mudou"
# Precisa de: keystore.properties (a chave de assinatura, fora do git) e `gh` com a conta iamSlokker.
set -euo pipefail

VERSION="${1:?Uso: scripts/release.sh <versão> [notas]}"
NOTES="${2:-}"
RELEASES_REPO="GOAThings/GoatLauncher-releases"
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
cp app/build/outputs/apk/release/app-release.apk "$apk"
# Cópia com nome fixo: https://github.com/$RELEASES_REPO/releases/latest/download/goatlauncher.apk dá sempre
# a versão mais recente (é para aí que aponta o código do Downloader, que nunca muda).
latest="build/goatlauncher.apk"
cp "$apk" "$latest"

git commit -qam "Versão $VERSION"
git tag -a "v$VERSION" -m "Versão $VERSION"  # anotada: o --follow-tags só envia tags anotadas
git push -q --follow-tags

gh release create "v$VERSION" "$apk" "$latest" --repo "$RELEASES_REPO" --title "GoatLauncher $VERSION" --notes "${NOTES:-Versão $VERSION}"
echo "Publicada a $VERSION em https://github.com/$RELEASES_REPO/releases"
