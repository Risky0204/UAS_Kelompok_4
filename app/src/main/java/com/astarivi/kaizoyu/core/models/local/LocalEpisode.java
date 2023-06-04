package com.astarivi.kaizoyu.core.models.local;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.astarivi.kaizolib.kitsu.model.KitsuEpisode;
import com.astarivi.kaizoyu.core.models.Episode;
import com.astarivi.kaizoyu.core.storage.database.data.embedded.EmbeddedEpisode;

import org.jetbrains.annotations.NotNull;

import java.util.Date;


public class LocalEpisode extends Episode {
    protected final Date watchDate;
    protected final int currentPosition;

    public LocalEpisode(@NotNull KitsuEpisode episode, int animeId, int currentPosition,
                        Date watchDate) {
        super(episode, animeId);
        this.watchDate = watchDate;
        this.currentPosition = currentPosition;
    }

    public Date getWatchDate() {
        return watchDate;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public EmbeddedEpisode toEmbeddedDatabaseObject() {
        KitsuEpisode kitsuEpisode = getKitsuEpisode();

        return new EmbeddedEpisode(
                Integer.parseInt(kitsuEpisode.id),
                this.animeId,
                kitsuEpisode.attributes.number,
                kitsuEpisode.attributes.seasonNumber != null ? kitsuEpisode.attributes.seasonNumber: 0,
                kitsuEpisode.attributes.relativeNumber != null ? kitsuEpisode.attributes.relativeNumber : 0,
                kitsuEpisode.attributes.length != null ? kitsuEpisode.attributes.length : 0,
                currentPosition
        );
    }

    // region Parcelable implementation

    protected LocalEpisode(@NonNull Parcel parcel) {
        super(parcel);
        watchDate = new Date(parcel.readLong());
        currentPosition = parcel.readInt();
    }

    public static final Parcelable.Creator<LocalEpisode> CREATOR = new Parcelable.Creator<LocalEpisode>() {
        @Override
        public LocalEpisode createFromParcel(Parcel parcel) {
            return new LocalEpisode(parcel);
        }

        @Override
        public LocalEpisode[] newArray(int size) {
            return new LocalEpisode[size];
        }
    };

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeLong(watchDate.getTime());
        dest.writeInt(currentPosition);
    }

    // endregion
}
