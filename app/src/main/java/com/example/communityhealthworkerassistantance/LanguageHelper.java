package com.example.communityhealthworkerassistantance;

public class LanguageHelper {

    public static class Lang {
        public final String code;
        public final String nativeName;
        public final String englishName;
        public final String speechTag;

        Lang(String code, String nativeName, String englishName, String speechTag) {
            this.code = code;
            this.nativeName = nativeName;
            this.englishName = englishName;
            this.speechTag = speechTag;
        }

        @Override
        public String toString() {
            if (nativeName.equals(englishName)) {
                return nativeName;
            }
            return nativeName + " (" + englishName + ")";
        }
    }

    public static final Lang[] ALL = new Lang[]{
            new Lang("en", "English", "English", "en-IN"),
            new Lang("hi", "हिन्दी", "Hindi", "hi-IN"),
            new Lang("kn", "ಕನ್ನಡ", "Kannada", "kn-IN"),
            new Lang("ta", "தமிழ்", "Tamil", "ta-IN"),
            new Lang("te", "తెలుగు", "Telugu", "te-IN"),
            new Lang("ml", "മലയാളം", "Malayalam", "ml-IN"),
            new Lang("mr", "मराठी", "Marathi", "mr-IN"),
            new Lang("bn", "বাংলা", "Bengali", "bn-IN"),
            new Lang("gu", "ગુજરાતી", "Gujarati", "gu-IN"),
            new Lang("pa", "ਪੰਜਾਬੀ", "Punjabi", "pa-IN"),
            new Lang("or", "ଓଡ଼ିଆ", "Odia", "or-IN"),
            new Lang("as", "অসমীয়া", "Assamese", "as-IN"),
            new Lang("ur", "اردو", "Urdu", "ur-IN")
    };

    public static int indexOf(String code) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].code.equals(code)) {
                return i;
            }
        }
        return 0;
    }

    public static Lang find(String code) {
        return ALL[indexOf(code)];
    }
}