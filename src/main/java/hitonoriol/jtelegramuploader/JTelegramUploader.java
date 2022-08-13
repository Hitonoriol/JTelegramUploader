package hitonoriol.jtelegramuploader;

public class JTelegramUploader {
	private final static String BOT_TOKEN_VAR = "UPLOADER_BOT_TOKEN";
	private final static String
			COMPRESSED_FLAG = "-compressed",
			CHAT_ID_OPT = "--send-to",
			UPLOAD_DIR_OPT = "--dir",
			BOT_TOKEN_OPT = "--bot-token",
			IMG_COMPRESSION_OPT = "--compression-quality",
			IMG_SCALE_OPT = "--scale-factor";

	public static void main(String[] argv) {
		ArgParser args = new ArgParser(argv);
		if (args.invalid())
			return;

		if (argv.length < 2 || !args.optionExists(CHAT_ID_OPT) || !args.optionExists(UPLOAD_DIR_OPT)) {
			printHelp();
			return;
		}

		String botToken = args.optionExists(BOT_TOKEN_OPT)
				? args.getOption(BOT_TOKEN_OPT)
				: System.getenv(BOT_TOKEN_VAR);

		if (botToken == null) {
			printBotTokenHelp();
			return;
		}

		Uploader uploader = new Uploader(botToken, Long.parseLong(args.getOption(CHAT_ID_OPT)));
		
		if (args.optionExists(IMG_COMPRESSION_OPT))
			uploader.getImageCompressor().setCompressionQuality(args.getFloatOption(IMG_COMPRESSION_OPT));
		
		if (args.optionExists(IMG_SCALE_OPT))
			uploader.getImageCompressor().setScaleFactor(args.getFloatOption(IMG_SCALE_OPT));
		
		uploader.uploadDirectory(args.getOption(UPLOAD_DIR_OPT), args.flagExists(COMPRESSED_FLAG));
	}

	private static void printHelp() {
		System.out.println(
				  "Required args:\n"
				+ "  --dir <directory path>  :  Directory to upload files from\n"
				+ "  --send-to <telegram chat_id>  :  Telegram chat_id to send files to\n"
				+ "Optional args:\n"
				+ "  -compressed  :  Send images as photos\n"
				+ "  --compression-quality  :  Set compression quality for large photos [0.0 - 1.0]\n"
				+ "  --scale-factor  :  Set scale factor for large photos (any positive floating point number)"
		);
		printBotTokenHelp();
	}

	private static void printBotTokenHelp() {
		System.out.println(
				  "Bot token can be set using one of these two options:\n"
				+ "  * Via an environment variable `UPLOADER_BOT_TOKEN`\n"
				+ "  * Via a command line option: `--bot-token <token>`"
		);
	}
}