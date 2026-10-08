package com.Reservation.Hotel.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * DESIGN PATTERN - Template Method.
 *
 * The steps for handling uploaded photos are always the same - drop empty parts, check the count,
 * size and type, save under a random name, return the public URL, delete safely. Those steps are
 * implemented once here ({@link #validate}, {@link #store}, {@link #delete} are the template methods);
 * subclasses only fill in the parts that differ: which folder, how many photos, and what to call the
 * thing in error messages. See {@link HotelImageStorageService} and {@link RoomImageStorageService}.
 */
public abstract class ImageStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB per photo
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    // ---------------- hooks the subclasses provide ----------------

    /** Folder under ./uploads, e.g. "hotels". Files are served at /uploads/&lt;folder&gt;/&lt;file&gt;. */
    protected abstract String folder();

    /** Maximum number of photos one item may have. */
    public abstract int maxImages();

    /** What the photos belong to, used in messages, e.g. "hotel". */
    protected abstract String itemName();

    // ---------------- template methods (shared algorithm) ----------------

    private Path root() {
        return Paths.get("uploads", folder());
    }

    private String publicPrefix() {
        return "/uploads/" + folder() + "/";
    }

    /** Drops the empty parts browsers send when no file was chosen. */
    public final List<MultipartFile> nonEmpty(MultipartFile[] files) {
        List<MultipartFile> result = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) result.add(f);
            }
        }
        return result;
    }

    /**
     * @param newFiles    the photos being uploaded now
     * @param alreadyKept how many photos the item keeps from before (0 when registering)
     * @return an error message, or null when everything is fine
     */
    public final String validate(List<MultipartFile> newFiles, int alreadyKept) {
        if (alreadyKept + newFiles.size() > maxImages()) {
            return "A " + itemName() + " can have at most " + maxImages() + " photos.";
        }
        for (MultipartFile f : newFiles) {
            String name = f.getOriginalFilename() == null ? "file" : f.getOriginalFilename();
            if (f.getSize() > MAX_BYTES) {
                return "\"" + name + "\" is larger than 5 MB.";
            }
            String contentType = f.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                    || !ALLOWED_EXTENSIONS.contains(extensionOf(name))) {
                return "\"" + name + "\" is not a supported image (use JPG, PNG or WEBP).";
            }
        }
        return null;
    }

    /** Saves the files and returns their public URL paths, in the same order. */
    public final List<String> store(List<MultipartFile> files) throws IOException {
        List<String> paths = new ArrayList<>();
        if (files.isEmpty()) return paths;
        Files.createDirectories(root());
        for (MultipartFile f : files) {
            // Never trust the uploaded name - only keep a whitelisted extension.
            String fileName = UUID.randomUUID() + "." + extensionOf(f.getOriginalFilename());
            Files.copy(f.getInputStream(), root().resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            paths.add(publicPrefix() + fileName);
        }
        return paths;
    }

    /** Saves raw JPEG bytes (used by the demo-data seeder) and returns the public URL path. */
    public final String storeJpeg(byte[] data) throws IOException {
        Files.createDirectories(root());
        String fileName = UUID.randomUUID() + ".jpg";
        Files.write(root().resolve(fileName), data);
        return publicPrefix() + fileName;
    }

    /** Best-effort delete of a stored photo; ignores anything that isn't one of ours. */
    public final void delete(String publicPath) {
        if (publicPath == null || !publicPath.startsWith(publicPrefix())) return;
        try {
            Path file = Paths.get(publicPath).getFileName();
            if (file != null) Files.deleteIfExists(root().resolve(file.toString()));
        } catch (IOException ignored) {
            // A leftover file is harmless; don't fail the request because of it.
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
