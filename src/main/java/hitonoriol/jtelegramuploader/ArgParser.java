package hitonoriol.jtelegramuploader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ArgParser {

	private List<String> flags = new ArrayList<>();
	private HashMap<String, String> options = new HashMap<>();
	private boolean invalid = false;

	public ArgParser(String[] args) {
		if (args == null)
			return;

		try {
			parse(args);
		} catch (Exception e) {
			invalid = true;
			e.printStackTrace();
		}
	}

	private void parse(String[] args) throws Exception {
		for (int i = 0; i < args.length; i++) {
			switch (args[i].charAt(1)) {
			case '-':
				if (args[i].length() < 3)
					throw new Exception("Invalid option name [" + args[i] + "]");

				if (args.length - 1 == i)
					throw new Exception("Option must be followed by an argument [" + args[i] + "]");

				options.put(args[i], args[i + 1]);
				i++;
				break;

			default:
				flags.add(args[i]);
				break;
			}
		}
	}

	public boolean invalid() {
		return invalid;
	}
	
	public boolean flagExists(String arg) {
		return flags.contains(arg);
	}

	public boolean optionExists(String option) {
		return options.containsKey(option);
	}

	public String getOption(String option) {
		return options.get(option);
	}

	public float getFloatOption(String option) {
		return Float.parseFloat(getOption(option));
	}
}