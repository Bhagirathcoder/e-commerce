package com.bytebazaar.service;

import com.bytebazaar.exception.InvalidRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Stores validated images (JPG/JPEG/PNG/WEBP, max 5 MB) under uploads/. Executables are rejected by type + magic bytes. */
@Service
public class FileStorageService {
    private static final Set<String> EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> MIME = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX = 5L * 1024 * 1024;
    private final Path root;

    public FileStorageService(@Value("${bytebazaar.upload-dir:uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    public String store(MultipartFile f, String folder) {
        if (f == null || f.isEmpty()) throw new InvalidRequestException("No file selected");
        if (f.getSize() > MAX) throw new InvalidRequestException("Image is too large (max 5 MB)");
        String orig = Optional.ofNullable(f.getOriginalFilename()).orElse("");
        String ext = orig.contains(".") ? orig.substring(orig.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!EXT.contains(ext) || f.getContentType() == null || !MIME.contains(f.getContentType().toLowerCase()))
            throw new InvalidRequestException("Only JPG, JPEG, PNG or WEBP images are allowed");
        try {
            byte[] head = new byte[12];
            try (InputStream in = f.getInputStream()) { in.readNBytes(head, 0, 12); }
            if (!magicOk(head)) throw new InvalidRequestException("File content is not a valid image");
            Path dir = root.resolve(folder).normalize();
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + "." + ext;
            try (InputStream in = f.getInputStream()) { Files.copy(in, dir.resolve(name)); }
            return "/uploads/" + folder + "/" + name;
        } catch (IOException e) {
            throw new InvalidRequestException("Could not save the image");
        }
    }

    public List<String> storeAll(List<MultipartFile> files, String folder, int max) {
        List<String> out = new ArrayList<>();
        if (files == null) return out;
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            if (out.size() >= max) throw new InvalidRequestException("You can upload at most " + max + " images");
            out.add(store(f, folder));
        }
        return out;
    }

    private boolean magicOk(byte[] h) {
        boolean jpg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
        boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
        return jpg || png || webp;
    }
}
