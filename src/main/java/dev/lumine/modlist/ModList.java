package dev.lumine.modlist;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.util.InclusiveRange;

public final class ModList implements ModInitializer {
	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(createCommand("modlist", true));
			dispatcher.register(createCommand("mods", false));
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> createCommand(String name, boolean full) {
		return Commands.literal(name)
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes(context -> full ? showFullList(context.getSource()) : showCompactList(context.getSource()));
	}

	private static int showCompactList(CommandSourceStack source) {
		List<ModContainer> mods = FabricLoader.getInstance().getAllMods().stream()
			.filter(container -> container.getContainingMod().isEmpty())
			.sorted(Comparator.comparing(container -> container.getMetadata().getName(), String.CASE_INSENSITIVE_ORDER))
			.toList();
		List<Pack> datapacks = selectedDatapacks(source);

		MutableComponent modLine = Component.literal(mods.size() == 1 ? "Mod (1): " : "Mods (" + mods.size() + "): ")
			.withStyle(ChatFormatting.GOLD);
		appendSeparated(modLine, mods.stream().map(ModList::modComponent).toList());
		modLine.append(Component.literal(";").withStyle(ChatFormatting.GOLD));

		MutableComponent datapackLine = Component.literal(datapacks.size() == 1 ? "Datapack (1): " : "Datapacks (" + datapacks.size() + "): ")
			.withStyle(ChatFormatting.GOLD);
		appendSeparated(datapackLine, datapacks.stream().map(ModList::datapackComponent).toList());

		source.sendSuccess(() -> modLine, false);
		source.sendSuccess(() -> datapackLine, false);
		return mods.size() + datapacks.size();
	}

	private static int showFullList(CommandSourceStack source) {
		List<ModContainer> allMods = FabricLoader.getInstance().getAllMods().stream().toList();
		List<ModContainer> rootMods = allMods.stream()
			.filter(container -> container.getContainingMod().isEmpty())
			.sorted(MOD_COMPARATOR)
			.toList();
		List<Pack> datapacks = selectedDatapacks(source);

		send(source, countHeader("Mod", allMods.size()));
		for (int i = 0; i < rootMods.size(); i++) {
			appendModTree(source, rootMods.get(i), "", i == rootMods.size() - 1, false);
		}

		send(source, countHeader("Datapack", datapacks.size()));
		appendDatapackTree(source, datapacks);
		return allMods.size() + datapacks.size();
	}

	private static void appendModTree(CommandSourceStack source, ModContainer mod, String prefix, boolean last, boolean nested) {
		MutableComponent line = Component.literal(prefix + (last ? "└── " : "├── ")).withStyle(ChatFormatting.GRAY)
			.append(modComponent(mod, nested ? ChatFormatting.GRAY : ChatFormatting.AQUA));
		send(source, line);

		List<ModContainer> children = mod.getContainedMods().stream().sorted(MOD_COMPARATOR).toList();
		String childPrefix = prefix + (last ? "    " : "│   ");
		for (int i = 0; i < children.size(); i++) {
			appendModTree(source, children.get(i), childPrefix, i == children.size() - 1, true);
		}
	}

	private static void appendDatapackTree(CommandSourceStack source, List<Pack> datapacks) {
		List<Pack> fabricDatapacks = new ArrayList<>();
		List<Pack> otherDatapacks = new ArrayList<>();
		for (Pack datapack : datapacks) {
			(isFabricModDatapack(datapack) ? fabricDatapacks : otherDatapacks).add(datapack);
		}

		int rootCount = otherDatapacks.size() + (fabricDatapacks.isEmpty() ? 0 : 1);
		int rootIndex = 0;
		if (!fabricDatapacks.isEmpty()) {
			boolean lastGroup = ++rootIndex == rootCount;
			send(source, Component.literal(lastGroup ? "└── Fabric Mod" : "├── Fabric Mod").withStyle(ChatFormatting.GRAY));
			String childPrefix = lastGroup ? "    " : "│   ";
			for (int i = 0; i < fabricDatapacks.size(); i++) {
				boolean last = i == fabricDatapacks.size() - 1;
				MutableComponent line = Component.literal(childPrefix + (last ? "└── " : "├── ")).withStyle(ChatFormatting.GRAY)
					.append(datapackComponent(fabricDatapacks.get(i), ChatFormatting.GRAY));
				send(source, line);
			}
		}

		for (Pack datapack : otherDatapacks) {
			boolean last = ++rootIndex == rootCount;
			MutableComponent line = Component.literal(last ? "└── " : "├── ").withStyle(ChatFormatting.GRAY)
				.append(datapackComponent(datapack, ChatFormatting.GREEN));
			send(source, line);
		}
	}

	private static List<Pack> selectedDatapacks(CommandSourceStack source) {
		return source.getServer().getPackRepository().getSelectedPacks().stream()
			.sorted(DATAPACK_COMPARATOR)
			.toList();
	}

	private static Component countHeader(String singular, int count) {
		String label = count == 1 ? singular : singular + "s";
		return Component.literal(label + " (" + count + "):").withStyle(ChatFormatting.GOLD);
	}

	private static void send(CommandSourceStack source, Component component) {
		source.sendSuccess(() -> component, false);
	}

	private static void appendSeparated(MutableComponent target, List<Component> entries) {
		for (int i = 0; i < entries.size(); i++) {
			if (i > 0) {
				target.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
			}
			target.append(entries.get(i));
		}
	}

	private static Component modComponent(ModContainer container) {
		return modComponent(container, ChatFormatting.AQUA);
	}

	private static Component modComponent(ModContainer container, ChatFormatting color) {
		ModMetadata metadata = container.getMetadata();
		String authors = metadata.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", "));
		if (authors.isBlank()) {
			authors = "None";
		}

		MutableComponent tooltip = labeledTooltipLine("Description", valueOrNone(metadata.getDescription()))
			.append("\n")
			.append(labeledTooltipLine("Authors", authors))
			.append("\n")
			.append(labeledTooltipLine("Version", metadata.getVersion().getFriendlyString()));

		return Component.literal(metadata.getName()).withStyle(style -> style
			.withColor(color)
			.withHoverEvent(new HoverEvent.ShowText(tooltip)));
	}

	private static Component datapackComponent(Pack pack) {
		return datapackComponent(pack, ChatFormatting.GREEN);
	}

	private static Component datapackComponent(Pack pack, ChatFormatting color) {
		MutableComponent tooltip = Component.literal("Description: ").withStyle(ChatFormatting.GRAY)
			.append(pack.getDescription().copy().withStyle(ChatFormatting.WHITE))
			.append("\n")
			.append(labeledTooltipLine("Compatibility", compatibilityName(pack.getCompatibility())));

		String version = datapackVersion(pack);
		if (version != null) {
			tooltip.append("\n").append(labeledTooltipLine("Version", version));
		}

		return pack.getTitle().copy().withStyle(style -> style
			.withColor(color)
			.withHoverEvent(new HoverEvent.ShowText(tooltip)));
	}

	private static boolean isFabricModDatapack(Pack pack) {
		try (var resources = pack.open()) {
			if (resources.anyMatch(ModPackResources.class::isInstance)) {
				return true;
			}
		} catch (RuntimeException exception) {
			// Fall through to the source check. Some third-party pack implementations may fail to open twice.
		}

		return pack.getPackSource().getClass().getName().startsWith("net.fabricmc.fabric.");
	}

	private static MutableComponent labeledTooltipLine(String label, String value) {
		return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}

	private static String valueOrNone(String value) {
		return value == null || value.isBlank() ? "None" : value;
	}

	private static String compatibilityName(PackCompatibility compatibility) {
		return switch (compatibility) {
			case COMPATIBLE -> "Compatible";
			case TOO_OLD -> "Too old";
			case TOO_NEW -> "Too new";
			case UNKNOWN -> "Unknown";
		};
	}

	private static String datapackVersion(Pack pack) {
		try (PackMetadataResources resources = pack.openMetadata()) {
			PackMetadataSection metadata = resources.getMetadataSection(PackMetadataSection.SERVER_TYPE);
			if (metadata == null) {
				return null;
			}

			InclusiveRange<PackFormat> formats = metadata.supportedFormats();
			PackFormat minimum = formats.minInclusive();
			PackFormat maximum = formats.maxInclusive();
			if (minimum.major() == Integer.MAX_VALUE || maximum.major() == Integer.MAX_VALUE) {
				return null;
			}

			return minimum.equals(maximum) ? minimum.toString() : minimum + " - " + maximum;
		} catch (IOException | RuntimeException exception) {
			return null;
		}
	}

	private static final Comparator<ModContainer> MOD_COMPARATOR = Comparator.comparing(
		container -> container.getMetadata().getName(), String.CASE_INSENSITIVE_ORDER
	);
	private static final Comparator<Pack> DATAPACK_COMPARATOR = Comparator.comparing(
		pack -> pack.getTitle().getString(), String.CASE_INSENSITIVE_ORDER
	);
}
