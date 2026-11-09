#!/bin/sh
# Spring Seed installer — Linux, macOS, and WSL.
#
#   curl -fsSL https://raw.githubusercontent.com/AymanHazem/Spring-Seed/main/install.sh | sh
#
# Environment variables:
#   SEED_VERSION          Release tag to install, e.g. v1.1.0 (default: latest)
#   SEED_INSTALL_DIR      Install directory (default: $HOME/.local/bin)
#   SEED_NO_MODIFY_PATH   Set to 1 to skip editing shell rc files
#   SEED_DOWNLOAD_BASE_URL  Override the download location (mirrors, local testing)

set -eu

REPO="AymanHazem/Spring-Seed"
BINARY="seed"

if [ -t 1 ]; then
    BOLD="$(printf '\033[1m')"
    GREEN="$(printf '\033[32m')"
    BRIGHT_GREEN="$(printf '\033[1;32m')"
    RED="$(printf '\033[31m')"
    BLUE="$(printf '\033[34m')"
    CYAN="$(printf '\033[36m')"
    YELLOW="$(printf '\033[33m')"
    DIM="$(printf '\033[2m')"
    RESET="$(printf '\033[0m')"
else
    BOLD=""; GREEN=""; BRIGHT_GREEN=""; RED=""; BLUE=""; CYAN=""; YELLOW=""; DIM=""; RESET=""
fi

step() { printf '  %s⟳%s %s\n' "$CYAN" "$RESET" "$*"; }
ok()   { printf '  %s✔%s %s\n' "$GREEN" "$RESET" "$*"; }
die()  { printf '\n  %s✗ Error:%s %s\n\n' "$RED" "$RESET" "$*" >&2; exit 1; }

detect_platform() {
    os="$(uname -s)"
    case "$os" in
        Linux)  os="linux" ;;
        Darwin) os="darwin" ;;
        MINGW*|MSYS*|CYGWIN*) die "native Windows is not supported; run this installer inside WSL." ;;
        *) die "unsupported operating system: $os" ;;
    esac

    arch="$(uname -m)"
    case "$arch" in
        x86_64|amd64)  arch="amd64" ;;
        arm64|aarch64) arch="arm64" ;;
        *) die "unsupported architecture: $arch" ;;
    esac

    # A shell running under Rosetta reports x86_64 on Apple Silicon; prefer the native build.
    if [ "$os" = "darwin" ] && [ "$arch" = "amd64" ] \
        && [ "$(sysctl -n sysctl.proc_translated 2>/dev/null || echo 0)" = "1" ]; then
        arch="arm64"
    fi

    echo "${os}-${arch}"
}

download() {
    url="$1"; out="$2"; show_progress="${3:-0}"
    if command -v curl >/dev/null 2>&1; then
        if [ "$show_progress" = "1" ] && [ -t 1 ]; then
            # Display real-time progress bar with curl
            curl -fL --retry 3 --progress-bar -o "$out" "$url"
        else
            curl -fsSL --retry 3 -o "$out" "$url"
        fi
    elif command -v wget >/dev/null 2>&1; then
        if [ "$show_progress" = "1" ] && [ -t 1 ]; then
            # Display progress bar with wget if supported
            if wget --help 2>&1 | grep -q -- '--show-progress'; then
                wget -q --show-progress --retry-connrefused -O "$out" "$url"
            else
                wget --retry-connrefused -O "$out" "$url"
            fi
        else
            wget -q -O "$out" "$url"
        fi
    else
        die "curl or wget is required"
    fi
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        echo ""
    fi
}

add_to_path() {
    dir="$1"
    case ":${PATH}:" in
        *":${dir}:"*) return 0 ;;
    esac

    if [ "${SEED_NO_MODIFY_PATH:-0}" = "1" ]; then
        NEEDS_PATH_HINT=1
        return 0
    fi

    shell_name="$(basename "${SHELL:-sh}")"
    case "$shell_name" in
        zsh)  rc="${ZDOTDIR:-$HOME}/.zshrc"; line="export PATH=\"${dir}:\$PATH\"" ;;
        bash)
            if [ "$(uname -s)" = "Darwin" ]; then rc="$HOME/.bash_profile"; else rc="$HOME/.bashrc"; fi
            line="export PATH=\"${dir}:\$PATH\"" ;;
        fish) rc="$HOME/.config/fish/config.fish"; line="fish_add_path \"${dir}\"" ;;
        *)    rc="$HOME/.profile"; line="export PATH=\"${dir}:\$PATH\"" ;;
    esac

    if [ -f "$rc" ] && grep -Fq "$dir" "$rc"; then
        NEEDS_PATH_HINT=1
        return 0
    fi

    mkdir -p "$(dirname "$rc")"
    printf '\n# Added by Spring Seed installer\n%s\n' "$line" >> "$rc"
    ok "Added ${dir} to PATH in ${rc}"
    NEEDS_PATH_HINT=1
}

main() {
    platform="$(detect_platform)"
    asset="${BINARY}-${platform}"
    version="${SEED_VERSION:-latest}"
    install_dir="${SEED_INSTALL_DIR:-$HOME/.local/bin}"
    NEEDS_PATH_HINT=0

    if [ -n "${SEED_DOWNLOAD_BASE_URL:-}" ]; then
        base_url="${SEED_DOWNLOAD_BASE_URL%/}"
    elif [ "$version" = "latest" ]; then
        base_url="https://github.com/${REPO}/releases/latest/download"
    else
        base_url="https://github.com/${REPO}/releases/download/${version}"
    fi

    printf '\n'
    printf '  %s╔══════════════════════════════════════════════╗%s\n' "$BRIGHT_GREEN" "$RESET"
    printf '  %s║          🌱  Spring Seed Installer           ║%s\n' "$BRIGHT_GREEN" "$RESET"
    printf '  %s╚══════════════════════════════════════════════╝%s\n\n' "$BRIGHT_GREEN" "$RESET"

    printf '  %sPlatform%s : %s\n' "$CYAN" "$RESET" "$platform"
    printf '  %sVersion %s : %s\n' "$CYAN" "$RESET" "$version"
    printf '  %sTarget  %s : %s/%s\n\n' "$CYAN" "$RESET" "$install_dir" "$BINARY"

    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT INT TERM

    step "Downloading ${asset}..."
    download "${base_url}/${asset}" "$tmp/$asset" 1 \
        || die "download failed: ${base_url}/${asset}
       No published ${version} release contains ${asset} yet. Check https://github.com/${REPO}/releases"

    ok "Download complete"

    step "Verifying SHA-256 checksum..."
    if download "${base_url}/checksums.txt" "$tmp/checksums.txt" 0 2>/dev/null; then
        expected="$(awk -v f="$asset" '$2 == f || $2 == "*"f {print $1}' "$tmp/checksums.txt")"
        actual="$(sha256_of "$tmp/$asset")"
        if [ -z "$expected" ]; then
            die "no checksum listed for ${asset}"
        elif [ -z "$actual" ]; then
            printf '     %s(skipped: sha256sum/shasum not found)%s\n' "$DIM" "$RESET"
        elif [ "$expected" != "$actual" ]; then
            die "checksum mismatch for ${asset} (expected: ${expected}, got: ${actual})"
        else
            ok "Checksum verified"
        fi
    else
        die "could not download checksums.txt from ${base_url}/checksums.txt"
    fi

    step "Installing executable to ${install_dir}/${BINARY}..."
    mkdir -p "$install_dir"
    chmod +x "$tmp/$asset"
    mv -f "$tmp/$asset" "$install_dir/$BINARY"
    ok "Installed successfully"

    step "Verifying binary execution..."
    installed_ver="$("$install_dir/$BINARY" --version 2>/dev/null || echo "")"
    if [ -n "$installed_ver" ]; then
        ok "Verified: ${installed_ver}"
    else
        die "installed binary failed to run; please check architecture compatibility or open an issue at https://github.com/${REPO}/issues"
    fi

    add_to_path "$install_dir"

    printf '\n'
    printf '  %s╔══════════════════════════════════════════════╗%s\n' "$BRIGHT_GREEN" "$RESET"
    printf '  %s║          🌱  Spring Seed is ready!           ║%s\n' "$BRIGHT_GREEN" "$RESET"
    printf '  %s╚══════════════════════════════════════════════╝%s\n\n' "$BRIGHT_GREEN" "$RESET"

    if [ "$NEEDS_PATH_HINT" = "1" ]; then
        printf '  %sNote:%s Open a new terminal session, or run:\n\n' "$YELLOW" "$RESET"
        printf '    %sexport PATH="%s:$PATH"%s\n\n' "$BOLD" "$install_dir" "$RESET"
    fi

    printf '  %sQuick Start:%s\n' "$CYAN" "$RESET"
    printf '    %sseed%s                                 %s# Interactive wizard%s\n' "$BOLD" "$RESET" "$DIM" "$RESET"
    printf '    %sseed init my-app -d web,data-jpa%s     %s# Instant generation%s\n' "$BOLD" "$RESET" "$DIM" "$RESET"
    printf '    %sseed update%s                          %s# Check for updates%s\n\n' "$BOLD" "$RESET" "$DIM" "$RESET"
    printf '  %sHappy coding!%s 🌱\n\n' "$BRIGHT_GREEN" "$RESET"
}

main "$@"
