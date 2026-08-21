package io.github.abenli06.mtrcoreperf.mixin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.mtr.core.simulation.FileLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes silent save-file corruption when a serialised object shrinks.
 *
 * <p>{@code Files.newOutputStream(path, CREATE)} does <em>not</em> imply
 * {@code TRUNCATE_EXISTING}. The implicit {@code CREATE, TRUNCATE_EXISTING, WRITE} default only
 * applies when the options array is empty; as soon as one option is passed, the set becomes
 * exactly that option plus {@code WRITE}. So a rewritten file that packs to fewer bytes than the
 * previous version keeps the tail of the old content, and the resulting MessagePack stream is
 * garbage past the new end.</p>
 *
 * <p>This is a correctness fix rather than an optimisation, and it applies to every dimension
 * folder MTR writes.</p>
 */
@Mixin(value = FileLoader.class, remap = false)
public class FileLoaderMixin {

	@Redirect(
			method = "writeDirtyDataToFile",
			at = @At(
					value = "INVOKE",
					target = "Ljava/nio/file/Files;newOutputStream(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/OutputStream;"
			),
			remap = false
	)
	private OutputStream mtrcoreperf$truncateBeforeWriting(Path path, OpenOption[] options) throws IOException {
		return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
	}
}
