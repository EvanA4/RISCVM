package net.eabbott.riscvm.context;

import net.eabbott.riscvm.util.Nullable;

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

    public static @Nullable Context parseArgs(String[] args) {
        Context context = new Context();
        try {
            context.configFile = context.getConfigFile(args);
            if (context.configFile != null) context.readConfigFile(context.configFile);
            context.readCommandLine(args);

            if (context.elfFile == null) {
                throw new IllegalArgumentException(
                    "Missing ELF file argument."
                );
            }

            if (context.outputFile != null) {
                context.logger.open(context.outputFile);
            }
            return context;

        } catch (Exception e) {
            context.logger.log("Failed to parse arguments: %s", e.getMessage());
            return null;
        }
    }

    @Override
    public void dump() {
        CacheContext[] caches = {this.l1Cache, this.l2Cache, this.l3Cache};
        logger.log("##### CONTEXT DUMP #####");
        logger.log("Config file: %s", configFile);
        logger.log("ELF file: %s", elfFile);
        logger.log("Output file: %s", outputFile);
        logger.log("Allow RV32M: %s", allowRV32M);
        logger.log("Allow RV32A: %s", allowRV32A);
        logger.log("RAM size: %d", ramSize);
        logger.log("Number of harts: %d", numHarts);
        logger.log("Cycle frequency: %d", cycleFrequency);
        logger.log("Cache depth: %d", cacheDepth);
        for (int i = 0; i < 3; ++i) {
            logger.log("L%d Cache:", i + 1);
            logger.log("\tAssociativity: %s", caches[i].ca.type);
            logger.log("\tWays: %d", caches[i].ca.ways);
            logger.log("\tSize: %d", caches[i].size);
            logger.log("\tBlock size: %d", caches[i].blockSize);
            logger.log("\tWrite policy: %s", caches[i].isWriteBack ? "write-back" : "write-through");
            logger.log("\tEviction policy: %s", caches[i].eviction);
        }
        logger.log("Cache coherency: %s", cacheCoherency);
        logger.log("Branch prediction rows: %d", branchPredictionRows);
        logger.log("Default prediction: %d", defaultPrediction);
        logger.log("Allow MMU: %s", allowMMU);
        logger.log("Number of TLB slots: %d", numTLBSlots);
        logger.log("TLB eviction Policy: %s", tlbEviction);
        logger.log("##### CONTEXT DUMP #####");
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

                String[] split = line.split(" ");
                List<String> words = new Vector<>();
                for (String word : split) {
                    if (!word.isBlank()) {
                        words.add(word);
                    }
                }
                if (words.size() % 2 != 0) {
                    throw new IllegalArgumentException(
                        "Argument in config file is either missing or split between lines."
                    );
                }

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
        for (int i = 0; i < args.length; i += 2) {
            String arg = args[i];

            // confirm argument is valid
            if (!VALID_ARGS_SET.contains(arg)) {
                throw new IllegalArgumentException(
                    String.format("Invalid argument: \"%s\".", arg)
                );
            }

            // confirm space for additional argument
            if (i == args.length - 1) {
                throw new IllegalArgumentException(
                    String.format("Missing value for argument: \"%s\".", arg)
                );
            }
            String next = args[i + 1];

            // unholy arg handling of despair :(
            switch (arg) {
                case "-f" -> {
                    this.configFile = next;
                }
                case "-i" -> {
                    this.elfFile = next;
                }
                case "-o" -> {
                    this.outputFile = next;
                }
                case "-m" -> {
                    this.allowRV32M = parseBoolean(next, "m", "on", "off");
                }
                case "-a" -> {
                    this.allowRV32A = parseBoolean(next, "a", "on", "off");
                }
                case "-mem" -> {
                    this.ramSize = parseMetric(next, "mem");
                }
                case "-harts" -> {
                    this.numHarts = parseInteger(next, "harts");
                }
                case "-hz" -> {
                    this.cycleFrequency = Math.toIntExact(parseMetric(next, "hz"));
                }
                case "-cL" -> {
                    this.cacheDepth = parseInteger(next, "cL");
                }
                case "-c1" -> {
                    this.l1Cache.ca = parseCacheAssociativity(next, "c1");
                }
                case "-c2" -> {
                    this.l2Cache.ca = parseCacheAssociativity(next, "c2");
                }
                case "-c3" -> {
                    this.l3Cache.ca = parseCacheAssociativity(next, "c3");
                }
                case "-cS1" -> {
                    this.l1Cache.size = parseMetric(next, "cS1");
                }
                case "-cS2" -> {
                    this.l2Cache.size = parseMetric(next, "cS2");
                }
                case "-cS3" -> {
                    this.l3Cache.size = parseMetric(next, "cS3");
                }
                case "-cB1" -> {
                    this.l1Cache.blockSize = parseInteger(next, "cB1");
                }
                case "-cB2" -> {
                    this.l2Cache.blockSize = parseInteger(next, "cB2");
                }
                case "-cB3" -> {
                    this.l3Cache.blockSize = parseInteger(next, "cB3");
                }
                case "-cW1" -> {
                    this.l1Cache.isWriteBack = parseBoolean(next, "cW1", "wb", "wt");
                }
                case "-cW2" -> {
                    this.l2Cache.isWriteBack = parseBoolean(next, "cW2", "wb", "wt");
                }
                case "-cW3" -> {
                    this.l3Cache.isWriteBack = parseBoolean(next, "cW3", "wb", "wt");
                }
                case "-cE1" -> {
                    this.l1Cache.eviction = parseEvictionPolicy(next, "cE1");
                }
                case "-cE2" -> {
                    this.l2Cache.eviction = parseEvictionPolicy(next, "cE2");
                }
                case "-cE3" -> {
                    this.l3Cache.eviction = parseEvictionPolicy(next, "cE3");
                }
                case "-cC" -> {
                    this.cacheCoherency = parseCacheCoherency(next, "cC");
                }
                case "-bp" -> {
                    this.branchPredictionRows = parseInteger(next, "bp");
                }
                case "-bpD" -> {
                    this.defaultPrediction = parseInteger(next, "bpD");
                }
                case "-mmu" -> {
                    this.allowMMU = parseBoolean(next, "mmu", "on", "off");
                }
                case "-tlb" -> {
                    this.numTLBSlots = parseInteger(next, "tlb");
                }
                case "-tlbE" -> {
                    this.tlbEviction = parseEvictionPolicy(next, "tlbE");
                }
                default -> {
                    // should never be reached
                    throw new IllegalArgumentException(
                        String.format("Invalid argument: \"%s\".", arg)
                    );
                }
            }
        }
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

    private CacheAssociativity parseCacheAssociativity(String value, String name) {
        if (value.length() < 2) {
            throw new IllegalArgumentException(
                String.format("Invalid argument for \"%s\": \"%s\"", name, value)
            );
        }

        String start = value.substring(0, 2);
        switch (start) {
            case "dm" -> {
                return new CacheAssociativity(CacheAssociativityType.DIRECT_MAPPED, -1);
            }
            case "fa" -> {
                return new CacheAssociativity(CacheAssociativityType.FULL_ASSOC, -1);
            }
            case "sa" -> {
                if (value.length() < 4) {
                    throw new IllegalArgumentException(
                        String.format("Invalid argument for \"%s\": \"%s\"", name, value)
                    );
                }
                int ways = parseInteger(value.substring(3), name);
                return new CacheAssociativity(CacheAssociativityType.SET_ASSOC, ways);
            }
            default -> {
                throw new IllegalArgumentException(
                    String.format("Invalid argument for \"%s\": \"%s\"", name, value)
                );
            }
        }
    }

    private int parseInteger(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format("Invalid argument for \"%s\": \"%s\"", name, value)
            );
        }
    }

    private CacheCoherency parseCacheCoherency(String value, String name) {
        switch (value) {
            case "none" -> {
                return CacheCoherency.NONE;
            }
            case "snoop" -> {
                return CacheCoherency.SNOOP;
            }
            case "dir" -> {
                return CacheCoherency.DIR;
            }
            default -> throw new IllegalArgumentException(
                String.format("Invalid argument for \"%s\": \"%s\"", name, value)
            );
        }
    }

    private EvictionPolicy parseEvictionPolicy(String value, String name) {
        switch (value) {
            case "fifo" -> {
                return EvictionPolicy.FIFO;
            }
            case "lru" -> {
                return EvictionPolicy.LRU;
            }
            case "lfu" -> {
                return EvictionPolicy.LFU;
            }
            default -> throw new IllegalArgumentException(
                String.format("Invalid argument for \"%s\": \"%s\"", name, value)
            );
        }
    }
}