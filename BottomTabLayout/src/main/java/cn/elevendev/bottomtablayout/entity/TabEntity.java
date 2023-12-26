package cn.elevendev.bottomtablayout.entity;


public class TabEntity {
    private int mIcon;
    private String mText;

    public TabEntity(int icon, String text) {
        mIcon = icon;
        mText = text;
    }

    public int getIcon() {
        return mIcon;
    }

    public void setIcon(int icon) {
        mIcon = icon;
    }

    public String getText() {
        return mText;
    }

    public void setText(String text) {
        mText = text;
    }
}
