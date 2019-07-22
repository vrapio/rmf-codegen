package com.commercetools;

import com.commercetools.models.Common.LocalizedString;
import com.commercetools.models.Common.LocalizedStringImpl;

import java.util.UUID;

public class CommercetoolsTestUtils {
    
    public static String randomString() {
        return "random-string-" + UUID.randomUUID().toString();
    }
    
    public static String randomId() {
        return "random-id-" + UUID.randomUUID().toString();
    }
    
    public static String randomKey() {
        return "random-key-" + UUID.randomUUID().toString();
    }
    
    public static LocalizedString randomLocalizedString() {
        LocalizedString localizedString = new LocalizedStringImpl();
        localizedString.setValue(randomString(), randomString());
        return localizedString;
    }
}