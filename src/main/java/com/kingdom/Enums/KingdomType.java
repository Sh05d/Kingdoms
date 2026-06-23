package com.kingdom.Enums;

public enum KingdomType {
    SPORTS("الرياضة"),
    KNOWLEDGE("المعرفة"),
    CHARITY("العمل الخيري"),
    GAMING("الألعاب"),
    VOLUNTEERING("التطوع"),
    FAITH("الإيمان"),
    NUTRITION("التغذية"),
    READING("القراءة"),
    PROGRAMMING("البرمجة");

    private final String arabicName;

    KingdomType(String arabicName) {
        this.arabicName = arabicName;
    }

    /** Arabic display name of the kingdom, e.g. for the join message ("انضممت إلى مملكة الرياضة"). */
    public String getArabicName() {
        return arabicName;
    }
}
