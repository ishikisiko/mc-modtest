package com.example.myvillage.client.townmap;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.worldplan.capital.CapitalGameBundle;
import com.example.myvillage.worldplan.townmap.TownMapLocation;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record TownMapData(String name, int width, int depth, int textureWidth, int textureHeight,
                          ResourceLocation texture, List<Place> places) {
    public enum Service {
        APOTHECARY("药", "药铺", "可交易药材与丹药，也可向药铺伙计询问试种。"),
        SMITH("锻", "铁作坊", "找铁匠查看铁作坊提供的交易。"),
        INN("宿", "客栈", "找客栈当值者交谈，查看客栈提供的交易。"),
        GUIDE("引", "向导亭", "向导会介绍城里的客栈、药铺和铁匠。"),
        TIANHENG("衡", "天衡殿", "琳琅阁购买秘籍，拍卖厅竞拍珍材和功法。");

        public final String glyph;
        public final String label;
        public final String description;

        Service(String glyph, String label, String description) {
            this.glyph = glyph;
            this.label = label;
            this.description = description;
        }
    }

    public record Place(Service service, String name, BlockPos entry) { }

    public TownMapData {
        places = List.copyOf(places);
    }

    static TownMapData load(TownMapLocation location) {
        String name = MyVillageMod.MOD_ID + ":town_maps/" + location.city() + ".json";
        byte[] bytes = CapitalGameBundle.readResource(name, 8192);
        var root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!root.get("city").getAsString().equals(location.city()) ||
                !root.get("bundle").getAsString().equals(location.bundle())) {
            throw new IllegalArgumentException("城镇地图与本世界实际省城不匹配");
        }
        int width = root.get("width").getAsInt(), depth = root.get("depth").getAsInt();
        int textureWidth = root.get("texture_width").getAsInt(), textureHeight = root.get("texture_height").getAsInt();
        if (width < 1 || width > 2048 || depth < 1 || depth > 2048 ||
                textureWidth < 1 || textureWidth > width || textureHeight < 1 || textureHeight > depth) {
            throw new IllegalArgumentException("城镇地图尺寸超出范围");
        }
        var types = EnumSet.noneOf(Service.class);
        var places = new ArrayList<Place>();
        for (var element : root.getAsJsonArray("services")) {
            var row = element.getAsJsonObject();
            var service = Service.valueOf(row.get("id").getAsString().toUpperCase(java.util.Locale.ROOT));
            var pos = row.getAsJsonArray("position");
            if (pos.size() != 3) throw new IllegalArgumentException("无效的城镇入口");
            var entry = new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt());
            var label = row.get("name").getAsString();
            if (!types.add(service) || label.isBlank() || label.length() > 64 ||
                    entry.getX() < 0 || entry.getX() >= width || entry.getZ() < 0 || entry.getZ() >= depth ||
                    entry.getY() < 0 || entry.getY() >= 320) {
                throw new IllegalArgumentException("无效的城镇建筑");
            }
            places.add(new Place(service, label, entry));
        }
        if (places.isEmpty()) throw new IllegalArgumentException("城镇缺少功能建筑");
        var texture = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID,
                "textures/gui/town_map/" + location.city() + ".png");
        return new TownMapData(root.get("name").getAsString(), width, depth, textureWidth, textureHeight, texture, places);
    }
}
