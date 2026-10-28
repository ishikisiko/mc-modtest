package com.example.myvillage.client.story;

import com.example.myvillage.story.net.StorySnapshotPayload;
import com.example.myvillage.story.realm.RealmIdentity;
import java.util.List;
import java.util.UUID;

/** Read-only projection shared by H's journey page and the O shortcut; no local quest transitions. */
public record JourneyView(String title, String status, String objective, String hint, List<String> journal, Travel travel) {
    public enum Travel {
        NONE("", ""), ENTER("进入阡镇", "qianzhen enter"), LEAVE("返回原世界", "qianzhen leave");
        public final String label;
        public final String command;
        Travel(String label, String command) { this.label=label; this.command=command; }
    }
    public static JourneyView of(StorySnapshotPayload snapshot, UUID player, String dimension) {
        boolean legacy=snapshot!=null && snapshot.accepted() && !snapshot.dimension().isEmpty()
                && !RealmIdentity.reserved(snapshot.dimension());
        Travel travel=player==null || legacy ? Travel.NONE
                : RealmIdentity.owner(dimension).filter(player::equals).isPresent() ? Travel.LEAVE : Travel.ENTER;
        if(snapshot==null || !snapshot.available()) return new JourneyView("阡镇行迹", "尚无行迹",
                "药庐正在招灵草学徒", "进入阡镇后，可向程砚问路，或去药庐询问沈岚。是否接受由你决定。", List.of(), travel);
        return new JourneyView(snapshot.title().isBlank()?"阡镇行迹":snapshot.title(),
                snapshot.completed()?"本章已完":snapshot.accepted()?"当前行迹":"尚未接取 · 可自行决定是否相助",
                snapshot.objective(),snapshot.hint(),List.copyOf(snapshot.journal()),travel);
    }
}
