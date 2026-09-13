package net.eabbott.riscvm.context;

import net.eabbott.riscvm.util.Nullable;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Context extends AbstractContext {
    private final String[] VALID_ARGS = {
            "-f", "-i", "-o", "-m", "-a", "-mem", "-harts", "-hz", "-cL", "-c1",
            "-c2", "-c3", "-cS1", "-cS2", "-cS3", "-cB1", "-cB2", "-cB3", "-cW1", "-cW2",
            "-cW3", "-cE1", "-cE2", "-cE3", "-cC", "-bp", "-bpD", "-mmu", "-tlb", "-tlbE"
    };
    private final Set<String> VALID_ARGS_SET = new HashSet<>(Arrays.stream(VALID_ARGS).toList());

    public Context(String[] args) {

    }

    @Override
    public void dump() {

    }

    private @Nullable String getConfigFile(String[] args) throws IllegalArgumentException {
        String output = null;
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-f")) {
                if (i != args.length - 1) {
                    output = args[i + 1];
                    break;
                } else {
                    throw new IllegalArgumentException("Missing file name for configuration file.");
                }
            }
        }
        return output;
    }

    private void readConfigFile(String fileName) {
        try (FileReader reader = new FileReader(fileName)){
            List<String> args = new Vector<>();

            List<String> lines = reader.readAllLines();
            for (String line : lines) {
                int ignoreIdx = line.indexOf("#");
                if (ignoreIdx != -1) line = line.substring(0, ignoreIdx);
                line = line.strip();
                if (line.isEmpty()) continue;

                String[] words = line.split(" ");
                for (String word : words) {
                    if (word.equals("i") || word.equals("f") || word.equals("o")) {
                        throw new IllegalArgumentException(
                                "Words \"i\", \"f\", and \"o\" are illegal in configuration files."
                        );
                    }
                    args.add(args.size() % 2 == 0 ? "-" + word : word);
                }
            }

            readCommandLine(args.toArray(new String[0]));

        } catch (IOException e) {
            throw new IllegalArgumentException(
                String.format("Failed to open %s: %s", fileName, e.getMessage())
            );
        }
    }

    private void readCommandLine(String[] args) {

    }

    private boolean parseBoolean(String value, String name, String truthy, String falsey) {
        if (value.equals(truthy)) return true;
        else if (value.equals(falsey)) return false;
        throw new IllegalArgumentException(
            String.format("Argument for \"%s\" must be %s\\%s, not \"%s\".", name, truthy, falsey, value)
        );
    }

    private long parseMetric(String value, String name) {
        for (int i = 0; i < value.length() - 1; ++i) {
            if (!Character.isDigit(value.charAt(i))) {
                throw new IllegalArgumentException(
                    String.format("Invalid numeric for argument \"%s\": \"%s\"", name, value)
                );
            }
        }

        char end = value.charAt(value.length() - 1);
        if (!Character.isDigit(end) && !Character.isLetter(end)) {
            throw new IllegalArgumentException(
                String.format("Invalid value for argument \"%s\": \"%s\"", name, value)
            );
        }

        if (Character.isDigit(end)) {
            return Long.parseLong(value);
        }

        if (end != 'K' && end != 'M' && end != 'G') {
            throw new IllegalArgumentException(
                String.format("Invalid metric prefix for argument \"%s\": \"%s\"", name, value)
            );
        }

        if (value.length() == 1) {
            throw new IllegalArgumentException(
                String.format("Invalid value for argument \"%s\": \"%s\"", name, value)
            );
        }

        long numeric = Long.parseLong(value.substring(0, value.length() - 1));
        return switch (end) {
            case 'K' -> numeric * 1_024;
            case 'M' -> numeric * 1_048_576;
            case 'G' -> numeric * 1_073_741_824;
            default -> throw new IllegalArgumentException(
                    String.format("Invalid value for argument \"%s\": \"%s\"", name, value)
            );
        };
    }
}