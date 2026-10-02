## 2024-05-24 - Path Traversal in Category Subfolders
**Vulnerability:** Found a Path Traversal vulnerability in `CategoryRepository.kt` where `subfolderName` was not sanitized before being used in file operations (e.g. `File(screenshotsDir, subfolderName)`). Malicious imports or user input could allow writing files outside the intended `Pictures/Screenshots/` directory.
**Learning:** Category models accept arbitrary strings, which are then directly used in file path concatenation. Even local databases or backup JSON imports can act as attack vectors.
**Prevention:** Always sanitize any user-controlled input or imported data that will be used to construct file paths or URIs, typically by stripping path traversal characters like `/`, `\`, and `..`.
