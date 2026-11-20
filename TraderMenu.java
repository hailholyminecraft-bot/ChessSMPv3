package com.chesssmp.trader;

import com.chesssmp.ChessSMP;
import com.chesssmp.classes.PieceType;
import com.chesssmp.gui.ConfirmMenu;
import com.chesssmp.gui.Items;
import com.chesssmp.gui.Menu;
import com.chesssmp.gui.PieceSelectMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TraderMenu extends Menu {

    private static final int[] OFFER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int PIECE_SLOT = 4;
    private static final int CLOSE_SLOT = 31;

    private final Map<Integer, TraderManager.Offer> slotOffers = new HashMap<>();

    public TraderMenu(ChessSMP plugin, Player viewer) {
        super(plugin, viewer, 4, plugin.messages().component("gui-trader-title"));
        render();
    }

    private List<Component> costLines(List<Cost> costs) {
        List<Component> lines = new ArrayList<>();
        for (Cost c : costs) {
            int have = plugin.trader().count(viewer, c.material());
            boolean ok = have >= c.amount();
            lines.add(Component.text((ok ? "\u2714 " : "\u2718 ") + c.amount() + "x " + Items.pretty(c.material())
                    + " (" + have + ")", ok ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        return lines;
    }

    private void render() {
        inventory.clear();
        slotOffers.clear();
        TraderManager trader = plugin.trader();

        List<Component> pieceLore = new ArrayList<>();
        pieceLore.add(Component.text("Current: ", NamedTextColor.GRAY)
                .append(plugin.classes().label(plugin.classes().pieceOf(viewer))));
        pieceLore.add(Component.empty());
        pieceLore.addAll(costLines(trader.pieceCost()));
        inventory.setItem(PIECE_SLOT, Items.glow(Items.make(Material.ENDER_EYE,
                plugin.messages().component("gui-change-piece"), pieceLore)));

        List<TraderManager.Offer> offers = trader.offers();
        for (int i = 0; i < offers.size() && i < OFFER_SLOTS.length; i++) {
            TraderManager.Offer offer = offers.get(i);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(offer.category(), NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.addAll(costLines(offer.cost()));
            lore.add(Component.empty());
            lore.add(plugin.messages().component("gui-click-to-buy"));
            Component name = Component.text(offer.amount() + "x " + offer.name(), NamedTextColor.WHITE);
            inventory.setItem(OFFER_SLOTS[i], Items.make(offer.material(), name, lore));
            slotOffers.put(OFFER_SLOTS[i], offer);
        }
        inventory.setItem(CLOSE_SLOT, Items.make(Material.BARRIER, plugin.messages().component("gui-close"), List.of()));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        TraderManager trader = plugin.trader();
        if (slot == PIECE_SLOT) {
            openPieceSelect();
            return;
        }
        TraderManager.Offer offer = slotOffers.get(slot);
        if (offer == null) {
            return;
        }
        if (trader.needsConfirm(offer.cost())) {
            List<Component> lines = costLines(offer.cost());
            new ConfirmMenu(plugin, viewer, plugin.messages().component("gui-confirm-title"),
                    Component.text(offer.amount() + "x " + offer.name(), NamedTextColor.WHITE), lines,
                    () -> {
                        trader.buyOffer(viewer, offer);
                        new TraderMenu(plugin, viewer).openLater();
                    },
                    () -> new TraderMenu(plugin, viewer).openLater()).openLater();
        } else {
            trader.buyOffer(viewer, offer);
            render();
        }
    }

    private void openPieceSelect() {
        TraderManager trader = plugin.trader();
        List<Component> extra = new ArrayList<>(costLines(trader.pieceCost()));
        extra.add(Component.empty());
        extra.add(plugin.messages().component("gui-click-to-select"));
        new PieceSelectMenu(plugin, viewer, plugin.messages().component("gui-piece-title"), extra,
                (player, piece) -> confirmPiece(piece),
                () -> new TraderMenu(plugin, viewer).openLater()).openLater();
    }

    private void confirmPiece(PieceType piece) {
        TraderManager trader = plugin.trader();
        if (plugin.classes().pieceOf(viewer) == piece) {
            plugin.messages().send(viewer, "choose-same");
            return;
        }
        // Changing piece is always confirmed - it is the most expensive trade.
        new ConfirmMenu(plugin, viewer, plugin.messages().component("gui-confirm-title"),
                Component.text("Change to ", NamedTextColor.WHITE).append(plugin.classes().label(piece)),
                costLines(trader.pieceCost()),
                () -> {
                    trader.buyPiece(viewer, piece);
                    new TraderMenu(plugin, viewer).openLater();
                },
                () -> new TraderMenu(plugin, viewer).openLater()).openLater();
    }
}
