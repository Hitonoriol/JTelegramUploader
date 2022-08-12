package hitonoriol.jtelegramuploader;

public class JTelegramUploader {
	private final static String BOT_TOKEN_VAR = "UPLOADER_BOT_TOKEN", CHAT_ID_VAR = "UPLOADER_CHAT_ID";
	private final static String COMPRESSED_FLAG = "compressed";

	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Args: <compressed / uncompressed> <directory path>");
			return;
		}

		Uploader uploader = new Uploader(System.getenv(BOT_TOKEN_VAR),
				Long.parseLong(System.getenv(CHAT_ID_VAR)));

		uploader.uploadDirectory(args[1], args[0].equals(COMPRESSED_FLAG));
	}

}
