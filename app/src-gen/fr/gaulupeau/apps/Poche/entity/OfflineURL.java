package fr.gaulupeau.apps.Poche.entity;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT. Enable "keep" sections if you want to edit. 
/**
 * Entity mapped to table "OFFLINE_URL".
 */
public class OfflineURL {

    private Long id;
    private String url;

    public OfflineURL() {
    }

    public OfflineURL(Long id) {
        this.id = id;
    }

    public OfflineURL(Long id, String url) {
        this.id = id;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
