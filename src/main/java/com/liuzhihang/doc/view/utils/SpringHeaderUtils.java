package com.liuzhihang.doc.view.utils;

import com.liuzhihang.doc.view.constant.HeaderConstant;
import com.liuzhihang.doc.view.dto.DocDescription;
import com.liuzhihang.doc.view.dto.Header;
import org.jetbrains.annotations.NotNull;

/**
 * @author liuzhihang
 * @date 2020/3/6 13:24
 */
public class SpringHeaderUtils {
    private SpringHeaderUtils() {}


    @NotNull
    public static Header buildJsonHeader() {

        Header header = new Header();
        header.setRequired(true);
        header.setName("Content-Type");
        header.setValue(HeaderConstant.APPLICATION_JSON);
        header.setDescription(DocDescription.EMPTY.with(DocDescription.Source.GENERATED, HeaderConstant.APPLICATION_JSON));


        return header;
    }

    @NotNull
    public static Header buildFormHeader() {

        Header header = new Header();
        header.setRequired(true);
        header.setName("Content-Type");
        header.setValue(HeaderConstant.APPLICATION_FORM);
        header.setDescription(DocDescription.EMPTY.with(DocDescription.Source.GENERATED, HeaderConstant.APPLICATION_FORM));


        return header;
    }

}
