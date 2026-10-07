package ru.rulhot.rAlchemist.database.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@DatabaseTable(tableName = "ralchemist_players")
public final class PlayerRecord {

    private static final int UUID_LENGTH = 36;
    private static final int COLOR_LENGTH = 64;

    @DatabaseField(id = true, columnName = "uuid", canBeNull = false, width = UUID_LENGTH)
    private @Nullable String uuid;

    @DatabaseField(columnName = "xp", canBeNull = false)
    private int xp;

    @DatabaseField(columnName = "last_mix", canBeNull = false)
    private long lastMix;

    @DatabaseField(columnName = "mix_day", canBeNull = false)
    private long mixDay;

    @DatabaseField(columnName = "mix_count", canBeNull = false)
    private int mixCount;

    @DatabaseField(columnName = "color", width = COLOR_LENGTH)
    private @Nullable String color;
}
