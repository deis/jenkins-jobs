generate-stub() {
  stub_arg="${1}"
  return_value="${2}"

  cat <<EOF
    case "\${1}" in
      ("${stub_arg}") echo '"${return_value}"' ;;
    esac
EOF
}
