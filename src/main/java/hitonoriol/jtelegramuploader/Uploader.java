package hitonoriol.jtelegramuploader;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaDocument;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;

public class Uploader {
	private Sender bot;
	private long chatId;
	private boolean exhaustiveGroups = false;
	private ImageCompressor imageCompressor = new ImageCompressor();

	private static final int MAX_FILESIZE = 0x3200000, MAX_PHOTO_SIZE = 0xA00000;
	private static final double MB = 1048576.0;
	private static final long INTERVAL = 61000;
	private static final int FILES_PER_MSG = 10, MSG_LIMIT = 20;
	private static final int MAX_RETRIES = 3;

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

	public ImageCompressor getImageCompressor() {
		return imageCompressor;
	}

	public void setExhaustiveMessageGeneration(boolean value) {
		exhaustiveGroups = value;
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
					.filter(fPath -> !Files.isDirectory(fPath))
					.filter(fPath -> {
						boolean uploadable = getFilesize(fPath) < MAX_FILESIZE;
						if (!uploadable)
							printf("Ignoring `%s`: too large to upload, max filesize is %f MB\n", fPath,
									MAX_FILESIZE / MB);
						return uploadable;
					})
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
		int totalFileCount = 0;
		int totalSize = 0;

		List<InputMedia> mediaGroup = new ArrayList<>();
		Iterator<File> it = files.iterator();
		Consumer<File> skipMedia = file -> {
			printf("Skipping `%s`\n", file.getAbsolutePath());
			it.remove();
		};
		while (it.hasNext() && mediaGroup.size() < FILES_PER_MSG) {
			if (totalSize >= MAX_FILESIZE)
				break;

			File file = it.next();
			InputMedia input = mediaFactory.get();
			++totalFileCount;

			/* Attempt to compress photos client-side as Telegram API limits them to 10 MB */
			if (input instanceof InputMediaPhoto
					&& getFilesize(file.toPath()) > MAX_PHOTO_SIZE) {
				printf("Compressing `%s` (too large for a photo)...\n",
						file.getName());
				File original = file;
				file = imageCompressor.compressImageFile(file);

				/* If compression failed, `file` doesn't exist */
				if (!file.exists()) {
					printf("Oops, compression failed\n");
					skipMedia.accept(file);
					continue;
				} else if (exhaustiveGroups)
					files.set(files.indexOf(original), file);

				/* If photo is still too large, skip it */
				if (getFilesize(file.toPath()) > MAX_PHOTO_SIZE) {
					printf("Compressed `%s`, but it's still too large\n", file.getName());
					skipMedia.accept(file);
					continue;
				}
			}

			long fileSize = getFilesize(file.toPath());

			if (totalSize + fileSize >= MAX_FILESIZE) {
				printf("No space left for `%s` in current group, leaving it for the next one\n",
						file.getName());
				if (exhaustiveGroups)
					continue;
				else
					break;
			}

			input.setMedia(file, file.getName());
			mediaGroup.add(input);
			totalSize += fileSize;
			it.remove();
		}

		printf("Prepared a media group of %d files, %fMB total\n", mediaGroup.size(), (totalSize / MB));
		printf("Total files walked for current media group: %d\n", totalFileCount);
		return mediaGroup;
	}

	private final static String OK_RESPONSE = "Done!";

	private String sendMediaGroup(List<InputMedia> media) {
		SendMediaGroup group = new SendMediaGroup();
		group.setChatId(chatId);
		group.setMedias(media);
		try {
			bot.execute(group);
			return OK_RESPONSE;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}

	private String sendMedia(InputMedia media) {
		try {
			if (media instanceof InputMediaPhoto) {
				SendPhoto photo = new SendPhoto();
				photo.setChatId(chatId);
				photo.setPhoto(new InputFile(media.getNewMediaFile()));
				bot.execute(photo);
			} else {
				SendDocument doc = new SendDocument();
				doc.setChatId(chatId);
				doc.setDocument(new InputFile(media.getNewMediaFile()));
				bot.execute(doc);
			}
			return OK_RESPONSE;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}

	private void uploadFiles(List<File> filesToUpload, Supplier<InputMedia> mediaFactory) {
		printf("Starting the upload of %d files\n", filesToUpload.size());
		printf("Files will be sent in %ssequential order\n", exhaustiveGroups ? "non-" : "");

		int filesSent = 0, retries = 0;
		long firstFileTime = System.currentTimeMillis();
		boolean groupSent = true;
		List<InputMedia> media = null;
		while (!filesToUpload.isEmpty()) {
			long timePassed = System.currentTimeMillis() - firstFileTime;

			if (groupSent)
				media = nextMediaGroup(filesToUpload, mediaFactory);
			else
				printf("Retrying (%d/%d)...\n", retries, MAX_RETRIES);

			if (filesSent + media.size() > MSG_LIMIT) {
				if (timePassed < INTERVAL)
					messageDelay(INTERVAL - timePassed);
				firstFileTime = System.currentTimeMillis();
				filesSent = 0;
			}

			filesSent += media.size();
			printf("Sending a group of %d files (%d left)...", media.size(), filesToUpload.size());

			String response = media.size() > 1 ? sendMediaGroup(media) : sendMedia(media.get(0));
			groupSent = response.equals(OK_RESPONSE);
			
			if (!groupSent)
				++retries;
			else if (retries > 0)
				retries = 0;
			
			if (retries > MAX_RETRIES) {
				printf("Gave up retrying :c\n");
				groupSent = true;
			}

			printf("    %s\n\n", response);
		}

		printf("All uploads finished!\n");
	}

	static void printf(String text, Object... args) {
		System.out.printf(text, args);
	}
}
