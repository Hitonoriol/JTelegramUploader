package hitonoriol.jtelegramuploader;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.mutable.MutableInt;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaDocument;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;

public class Uploader {
	private Sender bot;
	private long chatId;

	private static final int MAX_FILESIZE = 0x3200000;
	private static final long INTERVAL = 61000;
	private static final int FILES_PER_MSG = 10, MSG_LIMIT = 20;

	public Uploader(String botToken, long chatId) {
		bot = new Sender(botToken);
		this.chatId = chatId;
	}

	private static Supplier<InputMedia> photoFactory = () -> new InputMediaPhoto(),
			documentFactory = () -> new InputMediaDocument();

	private <T extends Serializable, Method extends BotApiMethod<T>> void execute(Method method) {
		try {
			bot.execute(method);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendText(String text) {
		SendMessage sendMsg = new SendMessage();
		sendMsg.setChatId(chatId);
		sendMsg.setText(text);
		execute(sendMsg);
	}

	public void uploadDirectory(String path, boolean compressed) {
		printf("Preparing to upload all files in directory: `%s` as %s\n", path, compressed ? "photos" : "documents");
		uploadFiles(getUploadableFiles(path), compressed ? photoFactory : documentFactory);
	}

	private static long getFilesize(Path path) {
		try {
			return Files.size(path);
		} catch (Exception e) {
			e.printStackTrace();
			return Long.MAX_VALUE;
		}
	}
	
	private List<File> getUploadableFiles(String path) {
		try {
			return Files.walk(Paths.get(path))
					.filter(fPath -> !Files.isDirectory(fPath) && getFilesize(fPath) < MAX_FILESIZE)
					.map(fPath -> fPath.toFile())
					.collect(Collectors.toList());
		} catch (Exception e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}

	private void messageDelay(long time) {
		try {
			System.out.printf("[!] Message rate exceeded. Waiting for %d ms...\n", time);
			Thread.sleep(time);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private List<InputMedia> nextMediaGroup(List<File> files, Supplier<InputMedia> mediaFactory) {
		MutableInt fileCount = new MutableInt(0);
		MutableInt totalSize = new MutableInt(0);
		List<InputMedia> mediaGroup = files
				.stream()
				.filter(file -> fileCount.incrementAndGet() <= FILES_PER_MSG)
				.filter(file -> totalSize.addAndGet(getFilesize(file.toPath())) < MAX_FILESIZE)
				.map(file -> {
					InputMedia input = mediaFactory.get();
					input.setMedia(file, file.getName());
					return input;
				})
				.collect(Collectors.toList());
		files.subList(0, mediaGroup.size()).clear();
		return mediaGroup;
	}

	private final static String OK_RESPONSE = "Done!";

	private String sendMediaGroup(SendMediaGroup group) {
		try {
			bot.execute(group);
			return OK_RESPONSE;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}

	private void uploadFiles(List<File> filesToUpload, Supplier<InputMedia> mediaFactory) {
		printf("Starting the upload of %d files\n", filesToUpload.size());

		int filesSent = 0;
		long firstFileTime = System.currentTimeMillis();
		boolean groupSent = true;
		List<InputMedia> media = null;
		while (!filesToUpload.isEmpty()) {
			long timePassed = System.currentTimeMillis() - firstFileTime;

			if (groupSent)
				media = nextMediaGroup(filesToUpload, mediaFactory);
			else
				printf("Retrying...\n");

			if (filesSent + media.size() > MSG_LIMIT) {
				if (timePassed < INTERVAL)
					messageDelay(INTERVAL - timePassed);
				firstFileTime = System.currentTimeMillis();
				filesSent = 0;
			}

			filesSent += media.size();
			printf("Sending a group of %d files...", media.size());

			SendMediaGroup group = new SendMediaGroup();
			group.setChatId(chatId);
			group.setMedias(media);
			String response = sendMediaGroup(group);
			groupSent = response.equals(OK_RESPONSE);

			printf("    %s\n", response);
		}

		printf("All uploads finished!\n");
	}

	static void printf(String text, Object... args) {
		System.out.printf(text, args);
	}
}
