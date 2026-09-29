# Formatum

Formatum is a small [Quarkus](https://quarkus.io/) REST service that turns structured
data, posted as JSON, into a typeset PDF document.

A client (in our case: Leuphana's DSpace/PubData institutional repository) posts an item's metadata as JSON.
Formatum

1. validates the metadata against a configurable field list,
2. renders it to Markdown, where section order, section headings and mandatory
   fields are driven entirely by that configuration and not by the code, and
3. hands the Markdown to [Pandoc](https://pandoc.org/) which produces a PDF via
   `pdflatex`, optionally using a LaTeX template.

The response is the PDF itself, streamed back to the caller. Nothing is persisted:
the intermediate Markdown file and the generated PDF are temporary files and are
deleted after the response has been written.

## Requirements

* Java 21 and Maven (use the bundled `./mvnw`)
* `pandoc` and `pdflatex` on the `PATH`. Both are baked into the provided container
  images, so running Formatum outside a container requires a local Pandoc + TeX Live
  installation
* Docker / Docker Compose for the container-based workflows

## Configuration

All settings use the prefix `formatum` and are bound to the Quarkus `@ConfigMapping`
interface `de.leuphana.escience.formatum.config.FormatumConfig`. The repository ships
no `application.properties`, so the values have to be supplied at runtime: as
environment variables, system properties or your own `application.properties`. Which
source wins, and how a property name maps to an environment variable, is standard
Quarkus behaviour and documented in the
[configuration reference](https://quarkus.io/guides/config-reference).

| Property | Environment variable | Required | Purpose |
| --- | --- | --- | --- |
| `formatum.metadata.config-path` | `FORMATUM_METADATA_CONFIG_PATH` | yes | Absolute path to the metadata configuration YAML. Read once at startup; an unreadable file aborts the boot. |
| `formatum.templates.path` | `FORMATUM_TEMPLATES_PATH` | yes | Directory containing the LaTeX templates (`<name>.latex`). |
| `formatum.templates.default-name` | `FORMATUM_TEMPLATES_DEFAULT_NAME` | no | Template used when a request does not name one. If unset, Pandoc's built-in default template is used. |

Neither the metadata configuration nor the templates are part of this repository
(both are excluded via `.gitignore`). They are provided to the running service from
outside: through the bind mount in the Compose setup, and by the deployment
otherwise.

### Metadata configuration

The YAML file lists every metadata field that may appear in the generated document.
The order of the list is the order of the sections in the resulting Markdown/PDF.

```yaml
metadata:
  - field: "key.example.first"      # metadata key as delivered in the request
    mandatory: true                 # request is rejected if the field is missing or empty
    displayName: "Erstes Beispiel"  # rendered as the section heading
  - field: "key.example.second"
    mandatory: false
    displayName: "Zweites Beispiel"
```

See `metadata-config.yml.example` for a minimal file. Fields present in the request
but absent from the configuration are ignored; configured fields absent from the
request produce no section.

`field`, `mandatory` and `displayName` are the only recognised keys. Parsing is
strict: an unknown key aborts the startup, so a typo is a boot failure rather than a
silently ignored setting. `mandatory` may be omitted and then defaults to `false`.

### How a field becomes a section

Rendering happens in `MarkdownService` and follows a few rules that matter when
writing either the configuration or a template:

* A field is emitted as a level-2 heading, `## <displayName>`, followed by its values.
* A field with several values becomes a bullet list, a single value a plain paragraph.
* The value `-` is treated as a placeholder and dropped. If a field holds nothing but
  placeholders, the whole section is omitted.
* Values are passed through **unescaped** into Markdown and therefore into LaTeX.
  Handling special characters is the template's job (see below).
* An empty array, or a value that is not an array, yields the heading without content.

## API

### `POST /api/documents`

* Request: `Content-Type: application/json`
* Response: the PDF as `application/octet-stream`, with
  `Content-Disposition: attachment; filename="metadata-summary.pdf"`

```json
{
  "template": "my-template",
  "metadata": {
    "dc.title": ["Greatest of all titles"],
    "dc.contributor.author": ["Doe, Jane", "Doe, John"],
    "DataCite.Description.Abstract": ["..."]
  }
}
```

* `template` (optional): template name without the `.latex` suffix. Omit it to fall
  back to `formatum.templates.default-name`.
* `metadata` (required): object of metadata key → array of values. Array entries are
  either plain strings or objects carrying a `value` key; anything else is rendered as
  its raw JSON representation.

Errors are returned as JSON:

```json
{ "error": "Fehlende Pflichtfelder: Titel/Title (dc.title)", "status": 400 }
```

| Status | Cause |
| --- | --- |
| 400 | missing `metadata` object, missing mandatory fields, invalid or unknown template name |
| 500 | Pandoc conversion failed (details are logged server-side, not returned) |

### The Pandoc invocation

For every request Formatum writes the rendered Markdown to a temporary file and runs:

```shell
pandoc --pdf-engine=pdflatex -f markdown+autolink_bare_uris \
       [--template <templates.path>/<name>.latex] \
       <input.md> -s -o <output.pdf>
```

`autolink_bare_uris` is deliberate: addresses arrive inside the metadata as bare text
(for example after an actor's name). Without the extension Pandoc treats them as body
text, and in the PDF the address is neither clickable nor breakable at a sensible
point. As a link the template stays in control of how it breaks, without losing the
target.

## Templates

A template is a Pandoc LaTeX template stored as `<templates.path>/<name>.latex`.
Template names are restricted to letters, digits, `-` and `_` (max. 64 characters);
an invalid or unknown name is rejected with HTTP 400.

**Which template is used** is decided in three steps:

1. the `template` field of the request, if present,
2. otherwise `formatum.templates.default-name`,
3. otherwise no `--template` at all, so Pandoc renders with its built-in default.

An unknown template is a 400 in both cases, including when it is the configured
default.

**What the template receives** is only `$body$`. Formatum passes Pandoc no `-V`
variables and no YAML metadata block, so `$title$`, `$author$` and `$date$` are always
empty. That is a deliberate decision and not a gap: which fields exist and what they
are called is defined entirely by `metadata-config.yml`, and promoting a few of them to
Pandoc variables would force the service to know which metadata key is "the title".
Title and authors are therefore ordinary `##` sections inside the body, exactly like
every other field.

The practical consequences for writing a template:

* Style the field headings by redefining `\subsection` (Pandoc maps `##` to it), not
  through Pandoc variables. Counting the `\subsection` calls is a way to treat the
  leading fields differently from the rest, for instance to start a two-column layout
  after them.
* A template may declare variables of its own with an `$if(...)$` fallback, but since
  Formatum passes no variables, the fallback is what will always apply.
* The engine is `pdflatex`, and only packages present in the `pandoc/latex` image are
  available (geometry, xcolor, multicol, helvet, microtype, hyperref, xurl, babel,
  lmodern). Because Formatum does not escape metadata, characters that `pdflatex` does
  not know abort the run; catch them with `\DeclareUnicodeCharacter`, as is typically
  needed for non-breaking spaces, soft hyphens and similar.

To iterate on a template, drop the `.latex` file into `templates/` and run
`./create_demo_pdf.sh` (see below). The Compose setup mounts the working copy, so a
changed template takes effect on the next request, with no rebuild and no restart.

## Testing a template standalone

`create_demo_pdf.sh` posts a JSON payload to a running Formatum instance and writes
the resulting PDF to `out/`, named after the payload and a timestamp. This is the
quickest way to iterate on a LaTeX template or on `metadata-config.yml`, without
going through a calling application.

```shell
./create_demo_pdf.sh                                       # uses formatum_demo_payload.json
./create_demo_pdf.sh my_payload.json                       # any other payload file
./create_demo_pdf.sh formatum_demo_payload.json --no-open  # do not open the PDF in a viewer
FORMATUM_HOST=http://127.0.0.1:8181 ./create_demo_pdf.sh   # non-default host or port
```

The script fails loudly on a non-200 response or on a response that is not a PDF, and
pretty-prints the JSON error body when `jq` is available. `formatum_demo_payload.json`
is a representative metadata record covering a wide range of fields.

## Running the application

### Dev mode (Docker Compose)

The Compose setup builds `Dockerfile.dev` and starts Quarkus dev mode with live
reload. That image takes a `pandoc/latex` stage, adds `tlmgr install times` on top
because the base image does not ship the Times fonts, and copies Pandoc, `pdflatex`
and TeX Live from it into a Maven/Temurin image. The project directory is mounted at
`/formatum`, so `metadata-config.yml` and `templates/` from the working copy are
picked up.

```shell
./build.sh        # docker compose build --pull
./run.sh          # docker compose up
./run.sh down     # docker compose down (any argument brings the stack down)
```

* API: <http://localhost:8181/api/documents>
* Quarkus Dev UI: <http://localhost:8181/q/dev/>
* Remote debugger: port `5050`

### Dev mode (local)

Requires Pandoc and `pdflatex` locally. Without the container the service uses the
Quarkus default port 8080:

```shell
FORMATUM_METADATA_CONFIG_PATH=$PWD/metadata-config.yml \
FORMATUM_TEMPLATES_PATH=$PWD/templates \
./mvnw quarkus:dev
```

### Tests

```shell
./mvnw test
```

The suite runs without the gitignored production resources: `src/test/resources/application.properties`
points the two required paths at the fixtures `metadata-config-test.yml` and
`test-templates/`, so a fresh clone is enough. `PdfResourceControllerTest` is a
`@QuarkusTest` driving the endpoint; the remaining classes are plain unit tests.

### Packaging

Four alternatives, not a sequence. Pick the one matching the target artifact:

```shell
./mvnw package                                                # target/quarkus-app/quarkus-run.jar
./mvnw package -Dquarkus.package.jar.type=uber-jar            # everything in a single runner jar
./mvnw package -Dnative                                       # native executable (GraalVM/Mandrel)
./mvnw package -Dnative -Dquarkus.container-image.build=true  # native build plus container image
```

Packaging is plain Quarkus, so the remaining options and their prerequisites are
covered by the [Maven tooling guide](https://quarkus.io/guides/maven-tooling) and
[Building a native executable](https://quarkus.io/guides/building-native-image).

### Production

The production `Dockerfile` copies `target/*-runner` into a Quarkus micro image and
adds Pandoc, `pdflatex` and TeX Live from `pandoc/latex`. It expects the native
executable to exist already, so building the image is a two-step affair:

```shell
./mvnw package -Dnative     # produces target/*-runner
docker build -t formatum .
```

The resulting image listens on 8181, runs as UID 1001 and is stateless. What it needs
from the surrounding infrastructure is:

* **The metadata configuration and the templates**, since neither is part of the
  image. They have to be readable by UID 1001.
* **Two environment variables** naming those two paths inside the container,
  `FORMATUM_METADATA_CONFIG_PATH` and `FORMATUM_TEMPLATES_PATH`, plus the optional
  `FORMATUM_TEMPLATES_DEFAULT_NAME`.
* **A writable temporary directory.** Each request writes an intermediate Markdown
  file and a PDF there and deletes both afterwards. Nothing else is stored, so the
  container needs no volume of its own and can be replaced at any time.

The example below uses plain Docker for the sake of being concrete. Nothing about it
is specific to Docker, so translate it to whatever runs containers in your setup. Put
the configuration and the templates side by side in one directory, wherever such
things belong on your host:

```
<your config directory>/
├── metadata-config.yml
└── templates/
    └── my-template.latex
```

Only the paths inside the container are fixed by the two environment variables. The
host directory is yours to choose, so the example keeps it in a variable:

```shell
CONFIG_DIR=/path/to/your/config    # the directory shown above

docker run -d --name formatum -p 8181:8181 \
  -v "$CONFIG_DIR:/config:ro" \
  -e FORMATUM_METADATA_CONFIG_PATH=/config/metadata-config.yml \
  -e FORMATUM_TEMPLATES_PATH=/config/templates \
  -e FORMATUM_TEMPLATES_DEFAULT_NAME=my-template \
  formatum
```

Create that directory and its contents before the first start. A bind mount whose
source does not exist is not an error in Docker: it silently creates an empty
directory instead, and the service then aborts the boot with `Is a directory` or with
an empty configuration. Mounting the enclosing directory rather than the single YAML
file also keeps an editor that replaces the file on save from detaching the mount.

Three things are worth knowing when operating it:

* **The metadata configuration is read once at startup.** Changing it requires a
  restart, and a file that cannot be read aborts the boot rather than starting a
  degraded service. Templates, by contrast, are resolved per request, so adding or
  editing a `.latex` file takes effect immediately.
* **The port is fixed at 8181.** The entrypoint passes it as a system property, which
  takes precedence over environment variables, so `QUARKUS_HTTP_PORT` will not change
  it. Publish a different host port instead.

## License

BSD 3-Clause, see `LICENSE`.
  