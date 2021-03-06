package org.apache.cayenne.testdo.testmap.auto;

import java.math.BigDecimal;

import org.apache.cayenne.exp.Property;
import org.apache.cayenne.testdo.testmap.ArtDataObject;
import org.apache.cayenne.testdo.testmap.Artist;
import org.apache.cayenne.testdo.testmap.Gallery;
import org.apache.cayenne.testdo.testmap.PaintingInfo;

/**
 * Class _Painting was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _Painting extends ArtDataObject {

    private static final long serialVersionUID = 1L; 

    @Deprecated
    public static final String ESTIMATED_PRICE_PROPERTY = "estimatedPrice";
    @Deprecated
    public static final String PAINTING_DESCRIPTION_PROPERTY = "paintingDescription";
    @Deprecated
    public static final String PAINTING_TITLE_PROPERTY = "paintingTitle";
    @Deprecated
    public static final String TO_ARTIST_PROPERTY = "toArtist";
    @Deprecated
    public static final String TO_GALLERY_PROPERTY = "toGallery";
    @Deprecated
    public static final String TO_PAINTING_INFO_PROPERTY = "toPaintingInfo";

    public static final String PAINTING_ID_PK_COLUMN = "PAINTING_ID";

    public static final Property<BigDecimal> ESTIMATED_PRICE = new Property<BigDecimal>("estimatedPrice");
    public static final Property<String> PAINTING_DESCRIPTION = new Property<String>("paintingDescription");
    public static final Property<String> PAINTING_TITLE = new Property<String>("paintingTitle");
    public static final Property<Artist> TO_ARTIST = new Property<Artist>("toArtist");
    public static final Property<Gallery> TO_GALLERY = new Property<Gallery>("toGallery");
    public static final Property<PaintingInfo> TO_PAINTING_INFO = new Property<PaintingInfo>("toPaintingInfo");

    public void setEstimatedPrice(BigDecimal estimatedPrice) {
        writeProperty("estimatedPrice", estimatedPrice);
    }
    public BigDecimal getEstimatedPrice() {
        return (BigDecimal)readProperty("estimatedPrice");
    }

    public void setPaintingDescription(String paintingDescription) {
        writeProperty("paintingDescription", paintingDescription);
    }
    public String getPaintingDescription() {
        return (String)readProperty("paintingDescription");
    }

    public void setPaintingTitle(String paintingTitle) {
        writeProperty("paintingTitle", paintingTitle);
    }
    public String getPaintingTitle() {
        return (String)readProperty("paintingTitle");
    }

    public void setToArtist(Artist toArtist) {
        setToOneTarget("toArtist", toArtist, true);
    }

    public Artist getToArtist() {
        return (Artist)readProperty("toArtist");
    }


    public void setToGallery(Gallery toGallery) {
        setToOneTarget("toGallery", toGallery, true);
    }

    public Gallery getToGallery() {
        return (Gallery)readProperty("toGallery");
    }


    public void setToPaintingInfo(PaintingInfo toPaintingInfo) {
        setToOneTarget("toPaintingInfo", toPaintingInfo, true);
    }

    public PaintingInfo getToPaintingInfo() {
        return (PaintingInfo)readProperty("toPaintingInfo");
    }


}
