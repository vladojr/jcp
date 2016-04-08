package dPloy;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.Socket;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

public class Client {

	public static void main(final String[] args) throws Exception {
		final byte[] key = Server.md5(args[0]);
		final String[] addr = args[1].split(":");
		final String remote = args[2];
		final String local = args[3];

		try (final Socket client = new Socket(addr[0], Integer.valueOf(addr[1]));
				final DataOutputStream out = new DataOutputStream(new CipherOutputStream(client.getOutputStream(), Server.cipher(Cipher.ENCRYPT_MODE, key)));
				final ZipInputStream in = new ZipInputStream(new CipherInputStream(client.getInputStream(), Server.cipher(Cipher.DECRYPT_MODE, key)))) {
			System.out.println("Connected");

			out.writeUTF(remote);
			final File root = new File(local);
			put(root, out, -1);
			// cipher wont flush until padded (aes - 16).
			out.writeLong(Server.EOF);
			final byte[] rndPad = new byte[32];
			new Random().nextBytes(rndPad);
			out.write(rndPad);
			out.flush();
			final String rootPath = root.getAbsolutePath();
			while (get(rootPath, in))
				;

			System.out.println("Finished");
		}
	}

	static void put(final File f, final DataOutputStream out, final int rootOffset) throws Exception {
		final String abs = f.getAbsolutePath();
		if (f.isDirectory()) {
			for (final String c : f.list())
				put(new File(abs + File.separator + c), out, rootOffset == -1 ? abs.length() : rootOffset);
		} else if (rootOffset != -1) {
			final long size = f.length(), lastModified = f.lastModified();
			System.out.println("Notifying " + abs.substring(rootOffset) + " " + lastModified + " " + size);
			out.writeLong(size);
			out.writeUTF(abs.substring(rootOffset));
			out.writeLong(lastModified);
		}
	}

	static boolean get(final String rootPath, final ZipInputStream in) throws Exception {
		final ZipEntry e = in.getNextEntry();
		if (e == null) return false;

		System.out.println("Receiving " + e.getName() + " " + e.getLastModifiedTime().toMillis() + " " + e.getSize());
		final File f = new File(rootPath + "/" + e.getName());
		f.getParentFile().mkdirs();
		try (final FileOutputStream fout = new FileOutputStream(f)) {
			final byte[] buff = new byte[1024];
			int r;
			while ((r = in.read(buff)) != -1)
				fout.write(buff, 0, r);
			in.closeEntry();
			return true;
		} finally {
			f.setLastModified(e.getLastModifiedTime().toMillis());
		}
	}
}
