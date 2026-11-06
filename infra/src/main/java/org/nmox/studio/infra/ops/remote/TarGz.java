package org.nmox.studio.infra.ops.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * A minimal POSIX ustar writer: enough to carry a project to a server
 * where {@code tar xzf -} unpacks it. Regular files and directories only;
 * a symlink is skipped by the bundler (a link out of the project would
 * carry something the user did not mean to upload). Names longer than
 * ustar's 100+155 split are refused by name rather than silently cut.
 *
 * <p>Written in Java rather than spawning {@code tar} so the bundle needs
 * no local tool and the same bytes go up from every OS.
 */
public final class TarGz implements AutoCloseable {

    private final OutputStream out;
    private long written;

    public TarGz(OutputStream raw) throws IOException {
        this.out = new GZIPOutputStream(raw, 64 * 1024);
    }

    /** Bytes of tar stream written so far (before gzip). */
    public long written() {
        return written;
    }

    public void directory(String name) throws IOException {
        String n = name.endsWith("/") ? name : name + "/";
        header(n, 0, 0755, '5', 0);
    }

    public void file(String name, Path file) throws IOException {
        long size = Files.size(file);
        int mode = executable(file) ? 0755 : 0644;
        long mtime = Files.getLastModifiedTime(file).toMillis() / 1000;
        header(name, size, mode, '0', mtime);
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            long copied = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                copied += n;
            }
            if (copied != size) {
                throw new IOException(name + " changed while it was being bundled");
            }
            written += copied;
        }
        pad(size);
    }

    private static boolean executable(Path file) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            return perms.contains(PosixFilePermission.OWNER_EXECUTE);
        } catch (IOException | UnsupportedOperationException notPosix) {
            return file.toFile().canExecute() && !Files.isDirectory(file);
        }
    }

    private void header(String name, long size, int mode, char type, long mtime) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        String prefix = "";
        String base = name;
        if (nameBytes.length > 100) {
            int cut = name.lastIndexOf('/', name.length() - 2);
            if (cut <= 0) {
                throw new IOException("path too long for the bundle: " + name);
            }
            prefix = name.substring(0, cut);
            base = name.substring(cut + 1);
            if (prefix.getBytes(StandardCharsets.UTF_8).length > 155
                    || base.getBytes(StandardCharsets.UTF_8).length > 100) {
                throw new IOException("path too long for the bundle: " + name);
            }
        }
        byte[] h = new byte[512];
        put(h, 0, 100, base);
        octal(h, 100, 8, mode);
        octal(h, 108, 8, 0);
        octal(h, 116, 8, 0);
        octal(h, 124, 12, size);
        octal(h, 136, 12, mtime);
        for (int i = 148; i < 156; i++) {
            h[i] = ' ';
        }
        h[156] = (byte) type;
        put(h, 257, 6, "ustar");
        h[263] = '0';
        h[264] = '0';
        put(h, 265, 32, "root");
        put(h, 297, 32, "root");
        put(h, 345, 155, prefix);
        int sum = 0;
        for (byte b : h) {
            sum += b & 0xff;
        }
        octal(h, 148, 7, sum);
        h[155] = ' ';
        out.write(h);
        written += 512;
    }

    private void pad(long size) throws IOException {
        int rem = (int) (size % 512);
        if (rem != 0) {
            byte[] zeros = new byte[512 - rem];
            out.write(zeros);
            written += zeros.length;
        }
    }

    private static void put(byte[] h, int at, int len, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(b, 0, h, at, Math.min(len, b.length));
    }

    private static void octal(byte[] h, int at, int len, long value) {
        String s = Long.toOctalString(value);
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < len - 1; i++) {
            sb.append('0');
        }
        sb.append(s);
        byte[] b = sb.toString().getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, h, at, len - 1);
        h[at + len - 1] = 0;
    }

    @Override
    public void close() throws IOException {
        out.write(new byte[1024]);
        written += 1024;
        out.close();
    }
}
