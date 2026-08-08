# libs

Drop the exact MTR build your server runs into this directory, then point
`mtr_jar` in `../gradle.properties` at its filename.

    MTR-forge-4.0.5+1.20.1.jar

It is a `compileOnly` dependency used only to resolve the mixin targets, and is
never bundled into the output jar. It is not committed here because it is ~81 MB
and is not ours to redistribute — download it from the official MTR release page.
