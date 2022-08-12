package hitonoriol.jtelegramuploader;

import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;

public class Sender extends DefaultAbsSender {
	private String token;

	public Sender(String token) {
		super(new DefaultBotOptions());
		this.token = token;
	}

	@Override
	public String getBotToken() {
		return token;
	}

}
