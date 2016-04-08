package dPloy;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Server {

	static final long EOF = -1;

	static byte[] md5(final String pwd) throws Exception {
		final MessageDigest md5 = MessageDigest.getInstance("md5");
		md5.update(pwd.getBytes());
		return md5.digest();
	}

	static Cipher cipher(final int mode, final byte[] key) throws Exception {
		final Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
		c.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
		return c;
	}

	public static void main(final String[] args) throws Exception {
		final byte[] key = md5(args[0]);
		final String port = args[1];
		try (final ServerSocket srv = new ServerSocket(Integer.parseInt(port))) {
			System.out.println("Server listening at " + srv.getLocalSocketAddress().toString());
			while (true) {
				// ACL
				// symmetric key in file, must match on both machines
				// synchronize also deleted resources
				try (final Socket client = srv.accept();
						final DataInputStream in = new DataInputStream(new CipherInputStream(client.getInputStream(), Server.cipher(Cipher.DECRYPT_MODE, key)));
						final ZipOutputStream out = new ZipOutputStream(new CipherOutputStream(client.getOutputStream(), Server.cipher(Cipher.ENCRYPT_MODE, key)))) {
					out.setLevel(9);
					System.out.println(LocalDateTime.now().toString() + " " + client.getRemoteSocketAddress() + " connected");

					final File root = new File(in.readUTF());
					final HashMap<String, String> files = new HashMap<>();
					long size;
					while ((size = in.readLong()) != EOF)
						files.put(in.readUTF().replaceAll("\\\\", "/"), size + " " + (in.readLong() / 1000));
					put(root, files, out, root.getAbsolutePath().length());
				} finally {
					System.out.println(LocalDateTime.now().toString() + " disconnected");
				}
			}
		}
	}

	static void put(final File f, final HashMap<String, String> files, final ZipOutputStream out, final int rootOffset) throws Exception {
		if (f.isDirectory()) {
			for (final String c : f.list())
				put(new File(f.getAbsolutePath() + File.separator + c), files, out, rootOffset);
		} else {
			final String rel = f.getAbsolutePath().substring(rootOffset).replaceAll("\\\\", "/");
			final String current = files.get(rel);
			final long size = f.length(), lastModified = f.lastModified();
			if (current != null && current.equals(size + " " + (lastModified / 1000))) return;

			System.out.println("Sending " + rel + " " + lastModified + " " + size + "/" + current);

			final ZipEntry e = new ZipEntry(rel);
			e.setSize(size);
			e.setLastModifiedTime(FileTime.fromMillis(lastModified));
			out.putNextEntry(e);
			try (final FileInputStream fin = new FileInputStream(f)) {
				final byte[] buff = new byte[1024];
				int r;
				while ((r = fin.read(buff)) != -1)
					out.write(buff, 0, r);
			}
			out.closeEntry();
		}
	}
}
