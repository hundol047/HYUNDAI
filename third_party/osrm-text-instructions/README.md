# OSRM Text Instructions

Navigation instruction templates are generated from `osrm-text-instructions@0.15.0`, BSD-2-Clause.
Source: https://github.com/Project-OSRM/osrm-text-instructions.js

31 language/region packs are distributed offline in `core/src/commonMain/kotlin/io/cabinos/core/NavLanguage_*.kt`.
Korean instructions preserve the app's existing vocabulary. Other locales use the generated turn text,
with the original road name appended verbatim. Ordinal exits 1–10 are translated; larger exits retain
an explicit numeric `#N` after the generic roundabout instruction. No translator API is called.

Reproduce from the pinned npm package:

```bash
npm pack osrm-text-instructions@0.15.0
mkdir extracted
tar -xzf osrm-text-instructions-0.15.0.tgz -C extracted
node scripts/generate-navigation-languages.cjs extracted/package
```

The package license is retained here and bundled in Android assets and the static site's `licenses/` directory.
