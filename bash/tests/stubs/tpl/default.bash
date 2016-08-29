# this default stub can be used in bats tests as such:
#
# load stubs/tpl/default
# stub cmd "$(generate-stub "<cmd_arg>" "<cmd_return>")"
#
# e.g., to stub `docker run --args` to return "foo":
# stub docker "$(generate-stub "run" "foo")"

generate-stub() {
  stub_arg="${1}"
  return_value="${2}"

  cat <<EOF
    case "\${1}" in
      ("${stub_arg}") echo '"${return_value}"' ;;
    esac
EOF
}
