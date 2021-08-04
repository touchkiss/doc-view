package com.liuzhihang.doc.view.ui.treetable;


import com.liuzhihang.doc.view.dto.DocViewParamData;
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode;
import org.jdesktop.swingx.treetable.DefaultTreeTableModel;
import org.jdesktop.swingx.treetable.TreeTableNode;

/**
 * 字段树列表
 *
 * @author liuzhihang
 * @date 2021/5/28 14:11
 */
public class ParamTreeTableModel extends DefaultTreeTableModel {

    public static final String[] names = {"参数名", "类型", "必选", "描述"};

    public ParamTreeTableModel(TreeTableNode node) {

        super(node);
    }

    /**
     * 列类型
     */
    @Override
    public Class<?> getColumnClass(int col) {
        // Object.class
        return super.getColumnClass(col);
    }

    /**
     * 列数量
     */
    @Override
    public int getColumnCount() {

        return names.length;
    }

    /**
     * 表头显示的内容
     */
    @Override
    public String getColumnName(int column) {

        return names[column];
    }


    @Override
    public Object getValueAt(Object node, int column) {
        if (node instanceof DefaultMutableTreeTableNode) {

            Object userObject = ((DefaultMutableTreeTableNode) node).getUserObject();

            if (userObject instanceof DocViewParamData) {
                // "参数名", "类型", "必选", "描述"
                DocViewParamData docViewParamData = (DocViewParamData) userObject;
                switch (column) {
                    case 0:
                        return docViewParamData.getName();
                    case 1:
                        return docViewParamData.getType();
                    case 2:
                        return docViewParamData.getRequired();
                    case 3:
                        return docViewParamData.getDesc();
                }
            }
        }
        return "";
    }

    @Override
    public void setValueAt(Object value, Object node, int column) {

        super.setValueAt(value, node, column);
        if (node instanceof DefaultMutableTreeTableNode) {

            Object userObject = ((DefaultMutableTreeTableNode) node).getUserObject();
            if (userObject instanceof DocViewParamData) {
                // "参数名", "类型", "必选", "描述"
                DocViewParamData docViewParamData = (DocViewParamData) userObject;

                if (column == 2) {
                    docViewParamData.setRequired(String.valueOf(value).equalsIgnoreCase("true"));
                } else if (column == 3) {
                    docViewParamData.setDesc(String.valueOf(value));
                }

            }
        }

    }

    @Override
    public boolean isCellEditable(Object node, int column) {

        if (column == 2) {
            return true;
        }
        if (column == 3) {
            return true;
        }

        return false;

    }


}