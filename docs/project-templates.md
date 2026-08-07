# Your own project templates

Since v1.293.0 the New Project wizard reads user-authored templates
from `~/.nmox/templates.d/`. Drop a JSON file there and it appears in
the wizard beside the built-ins, labelled "· yours" — no IDE build, no
plugin.

## Schema

One template per file, files read in filename order:

```json
{
  "name": "Team API Starter",
  "description": "fastify + vitest, the way our team starts services",
  "files": {
    "package.json": "{\n  \"name\": \"{{name}}\"\n}\n",
    "src/server.js": "// {{name}} entry\n"
  }
}
```

- `name` (required) and `description` label the wizard entry.
- `files` (required) maps relative paths to file content. Declaration
  order is generation order.
- `{{name}}` in any path or content is replaced with the project name —
  the same variable spelling API Studio and `.http` files use.

## Rules

- Paths must stay inside the project: an absolute path, a `..` segment,
  a backslash, or a drive letter disqualifies the whole template (half
  a template is worse than none). The reason appears on the status
  line.
- A malformed file is skipped with a status note; the other drop-ins
  still load.
- At most 200 files and 2 MB of content per template.
- Generation writes exactly the declared files — no extra `.gitignore`
  or README is added. The template is the contract.

## Trust

A drop-in is data that may have been copied from anywhere, so a custom
template never receives the pre-trust the built-ins get: the first Run
asks the normal Workspace Trust question, and the wizard's optional
dependency install asks it up front. Templates you wrote yourself are
one Trust Workspace click away; templates you did not write deserve the
prompt.
