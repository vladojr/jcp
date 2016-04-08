package dPloy;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JCP {
	public static void main(final String[] args) throws Exception {
		if (args == null || (args.length != 1 && args.length != 2 && args.length != 4)) {
			System.out.println("usage");
			System.out.println("download mode: locapPort");
			System.out.println("server mode: pwd localPort");
			System.out.println("client mode: pwd remoteAddress:remotePort remotepath localPath");
			System.exit(1);
		}

		if (args.length == 1) {
			System.out.println("use nc <address> " + args[0] + " > /app/jcp.jar");
			try (final ServerSocket srv = new ServerSocket(Integer.parseInt(args[0]));
					final Socket client = srv.accept()) {
				client.getOutputStream().write(Files.readAllBytes(Paths.get(Server.class.getProtectionDomain().getCodeSource().getLocation().getFile().substring(1))));
			}
		} else if (args.length == 2)
			Server.main(args);
		else
			Client.main(args);
	}
}
