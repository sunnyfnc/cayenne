package org.apache.cayenne.testdo.testmap.auto;

import java.util.Date;
import java.util.List;

import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.exp.Property;
import org.apache.cayenne.testdo.testmap.ArtistExhibit;
import org.apache.cayenne.testdo.testmap.Gallery;

/**
 * Class _Exhibit was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _Exhibit extends CayenneDataObject {

    private static final long serialVersionUID = 1L; 

    @Deprecated
    public static final String CLOSING_DATE_PROPERTY = "closingDate";
    @Deprecated
    public static final String OPENING_DATE_PROPERTY = "openingDate";
    @Deprecated
    public static final String ARTIST_EXHIBIT_ARRAY_PROPERTY = "artistExhibitArray";
    @Deprecated
    public static final String TO_GALLERY_PROPERTY = "toGallery";

    public static final String EXHIBIT_ID_PK_COLUMN = "EXHIBIT_ID";

    public static final Property<Date> CLOSING_DATE = new Property<Date>("closingDate");
    public static final Property<Date> OPENING_DATE = new Property<Date>("openingDate");
    public static final Property<List<ArtistExhibit>> ARTIST_EXHIBIT_ARRAY = new Property<List<ArtistExhibit>>("artistExhibitArray");
    public static final Property<Gallery> TO_GALLERY = new Property<Gallery>("toGallery");

    public void setClosingDate(Date closingDate) {
        writeProperty("closingDate", closingDate);
    }
    public Date getClosingDate() {
        return (Date)readProperty("closingDate");
    }

    public void setOpeningDate(Date openingDate) {
        writeProperty("openingDate", openingDate);
    }
    public Date getOpeningDate() {
        return (Date)readProperty("openingDate");
    }

    public void addToArtistExhibitArray(ArtistExhibit obj) {
        addToManyTarget("artistExhibitArray", obj, true);
    }
    public void removeFromArtistExhibitArray(ArtistExhibit obj) {
        removeToManyTarget("artistExhibitArray", obj, true);
    }
    @SuppressWarnings("unchecked")
    public List<ArtistExhibit> getArtistExhibitArray() {
        return (List<ArtistExhibit>)readProperty("artistExhibitArray");
    }


    public void setToGallery(Gallery toGallery) {
        setToOneTarget("toGallery", toGallery, true);
    }

    public Gallery getToGallery() {
        return (Gallery)readProperty("toGallery");
    }


}
