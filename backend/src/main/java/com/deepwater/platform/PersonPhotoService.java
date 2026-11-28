package com.deepwater.platform;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Profile photos on the Deepwater profile.
 *
 * <p>Every upload is decoded and written again as a new JPEG of at most {@value #MAX_SIDE} px. That keeps each photo
 * small enough to live in the database (no separate file storage to pay for) and drops everything except the
 * pixels: EXIF data, including the GPS position phones write into photos, never reaches anyone else.
 * Who may see a photo is decided by the product showing it (see {@code AmberService.photo}).
 */
@Service
public class PersonPhotoService {
    public static final int MAX_PHOTOS = 4;
    static final int MAX_SIDE = 1080;
    static final int MIN_SIDE = 200;
    /** Refuse images that would take too much memory to decode, before decoding them. */
    static final long MAX_PIXELS = 40_000_000L;
    private static final float QUALITY = 0.85f;

    private final JdbcTemplate db;

    public PersonPhotoService(JdbcTemplate db) { this.db = db; }

    public List<Photo> list(String accountId) {
        return db.query("select id, position from person_photos where account_id=? order by position, created_at",
                (rs, row) -> new Photo(rs.getString(1), rs.getInt(2)), accountId);
    }

    @Transactional
    public Photo add(String accountId, byte[] upload) {
        Integer count = db.queryForObject("select count(*) from person_photos where account_id=?", Integer.class, accountId);
        if (count != null && count >= MAX_PHOTOS) throw bad("You can have up to " + MAX_PHOTOS + " photos. Remove one first.");
        byte[] jpeg = reencode(upload);
        Integer last = db.queryForObject("select coalesce(max(position), -1) from person_photos where account_id=?", Integer.class, accountId);
        Photo photo = new Photo(UUID.randomUUID().toString(), last == null ? 0 : last + 1);
        db.update("insert into person_photos(id,account_id,position,content_type,bytes) values(?,?,?,?,?)",
                photo.id(), accountId, photo.position(), "image/jpeg", jpeg);
        return photo;
    }

    @Transactional
    public void remove(String accountId, String photoId) {
        if (db.update("delete from person_photos where id=? and account_id=?", photoId, accountId) == 0) throw notFound();
        renumber(accountId, null);
    }

    /** Makes this photo the first one, which products use as the cover. */
    @Transactional
    public void makeFirst(String accountId, String photoId) {
        Integer owned = db.queryForObject("select count(*) from person_photos where id=? and account_id=?", Integer.class, photoId, accountId);
        if (owned == null || owned == 0) throw notFound();
        renumber(accountId, photoId);
    }

    /** The JPEG bytes of one of this account's photos, or 404. Callers decide whether the viewer may see it. */
    public byte[] bytes(String ownerAccountId, String photoId) {
        var rows = db.query("select bytes from person_photos where id=? and account_id=?", (rs, row) -> rs.getBytes(1), photoId, ownerAccountId);
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst();
    }

    private void renumber(String accountId, String firstId) {
        List<String> ids = new java.util.ArrayList<>(list(accountId).stream().map(Photo::id).toList());
        if (firstId != null && ids.remove(firstId)) ids.addFirst(firstId);
        for (int i = 0; i < ids.size(); i++) db.update("update person_photos set position=? where id=?", i, ids.get(i));
    }

    /** Decodes a JPEG, PNG, GIF or BMP and writes a fresh, metadata-free JPEG of at most MAX_SIDE px. */
    static byte[] reencode(byte[] upload) {
        if (upload == null || upload.length == 0) throw bad("Choose a photo to upload.");
        BufferedImage source;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) throw bad("Use a JPEG or PNG photo.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) throw bad("That photo is too large. Use one under 40 megapixels.");
                source = reader.read(0);
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ResponseStatusException status) throw status;
            throw bad("That file could not be read as a photo. Use a JPEG or PNG photo.");
        }
        if (Math.min(source.getWidth(), source.getHeight()) < MIN_SIDE) throw bad("Use a photo at least " + MIN_SIDE + " px wide and high.");
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE); // transparent PNG areas become white, not black
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally { g.dispose(); }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(target, null, null), param);
        } catch (IOException _) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The photo could not be saved.");
        } finally { writer.dispose(); }
        return out.toByteArray();
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found."); }

    /** A photo reference. The image itself is fetched separately, through a route that checks who is asking. */
    public record Photo(String id, int position) {}
}
