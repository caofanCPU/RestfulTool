package core.view.window.frame;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import core.beans.Request;
import core.utils.RestUtil;
import core.utils.SystemUtil;
import core.view.window.RestfulTreeCellRenderer;
import org.jdesktop.swingx.JXButton;
import org.jdesktop.swingx.JXTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ZhangYuanSheng
 */
public class WindowFrame extends JPanel {

    /**
     * 项目对象
     */
    private final Project project;
    private final RestDetail restDetail;

    /**
     * 按钮 - 扫描service
     */
    private JButton scanApi;
    /**
     * 树 - service列表
     */
    private JTree tree;

    /**
     * Create the panel.
     */
    public WindowFrame(@NotNull Project project) {
        this.project = project;
        this.restDetail = new RestDetail(project);
        this.restDetail.setCallback(this::renderRequestTree);

        GridBagLayout gridBagLayout = new GridBagLayout();
        gridBagLayout.columnWidths = new int[]{0, 0};
        gridBagLayout.rowHeights = new int[]{0, 0, 0};
        gridBagLayout.columnWeights = new double[]{1.0, Double.MIN_VALUE};
        gridBagLayout.rowWeights = new double[]{1.0, 1.0, Double.MIN_VALUE};
        setLayout(gridBagLayout);

        JPanel headPanel = new JPanel();
        GridBagConstraints gbcHeadPanel = new GridBagConstraints();
        gbcHeadPanel.weighty = 2.5;
        gbcHeadPanel.insets = JBUI.insetsBottom(5);
        gbcHeadPanel.fill = GridBagConstraints.BOTH;
        gbcHeadPanel.gridx = 0;
        gbcHeadPanel.gridy = 0;
        add(headPanel, gbcHeadPanel);
        headPanel.setLayout(new BorderLayout(0, 0));

        initView(headPanel);

        GridBagConstraints gbcBodyPanel = new GridBagConstraints();
        gbcBodyPanel.weighty = 1.0;
        gbcBodyPanel.fill = GridBagConstraints.BOTH;
        gbcBodyPanel.gridx = 0;
        gbcBodyPanel.gridy = 1;
        add(restDetail, gbcBodyPanel);

        initEvent();

        firstLoad();
    }

    private void initView(@NotNull JPanel headPanel) {
        JPanel toolPanel = new JPanel();
        headPanel.add(toolPanel, BorderLayout.NORTH);
        toolPanel.setLayout(new BorderLayout(0, 0));

        scanApi = new JXButton(AllIcons.Actions.Refresh);
        Dimension scanApiSize = new Dimension(24, 24);
        scanApi.setPreferredSize(scanApiSize);
        // 按钮设置为透明，这样就不会挡着后面的背景
        scanApi.setContentAreaFilled(true);
        // 去掉按钮的边框
        scanApi.setBorderPainted(false);
        toolPanel.add(scanApi, BorderLayout.WEST);

        JScrollPane scrollPaneTree = new JBScrollPane();
        scrollPaneTree.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        headPanel.add(scrollPaneTree, BorderLayout.CENTER);

        tree = new JXTree();
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        model.setRoot(new DefaultMutableTreeNode());
        tree.setCellRenderer(new RestfulTreeCellRenderer());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(false);
        scrollPaneTree.setViewportView(tree);

        // 快速搜索
        new TreeSpeedSearch(tree);
    }

    /**
     * 初始化事件
     */
    private void initEvent() {
        // 控制器扫描监听
        scanApi.addActionListener(e -> renderRequestTree());

        // RequestTree子项点击监听
        tree.addTreeSelectionListener(e -> {
            Request node = getTreeNodeRequest(tree);
            if (node == null) {
                return;
            }
            restDetail.setRequest(node);
        });

        // RequestTree子项双击监听
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    final int doubleClick = 2;
                    Request node = getTreeNodeRequest(tree);
                    if (node != null && e.getClickCount() == doubleClick) {
                        node.navigate(true);
                    }
                }
            }

            /**
             * 右键菜单
             */
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    tree.setSelectionPath(path);

                    Request request = getTreeNodeRequest(tree);
                    if (request == null) {
                        return;
                    }

                    Rectangle pathBounds = tree.getUI().getPathBounds(tree, path);
                    if (pathBounds != null && pathBounds.contains(e.getX(), e.getY())) {
                        popupMenu(tree, request, e.getX(), pathBounds.y + pathBounds.height);
                    }
                }
            }
        });
        // 按回车键跳转到对应方法
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    Request request = getTreeNodeRequest(tree);
                    if (request != null) {
                        request.navigate(true);
                    }
                }
            }
        });
    }

    private void firstLoad() {
        try {
            /*
            TODO Cannot use JavaAnnotationIndex to scan Spring annotations when the project is not IDE index
             项目未被 IDE index时，无法使用JavaAnnotationIndex扫描Spring注解
             solution:
              1. Tool Window is dynamically registered when the project index is completed.
                 待项目index完毕时才动态注册ToolWindow窗口
              2. If the project is not indexed, stop scanning service, add callback when index is completed, scan and render service tree.
                 项目未被index则停止扫描service，增加index完毕回调，扫描渲染serviceTree
             */
            renderRequestTree();
        } catch (Exception e) {
            DumbService.getInstance(project).showDumbModeNotification(
                    "The project has not been loaded yet. Please wait until the project is loaded and try again."
            );
        }
    }

    /**
     * 渲染Restful请求列表
     */
    private void renderRequestTree() {
        AtomicInteger controllerCount = new AtomicInteger();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(controllerCount.get());

        Map<String, List<Request>> allRequest = RestUtil.getAllRequest(project);
        allRequest.forEach((moduleName, requests) -> {
            DefaultMutableTreeNode item = new DefaultMutableTreeNode(String.format(
                    "[%d]%s",
                    requests.size(),
                    moduleName
            ));
            requests.forEach(request -> {
                item.add(new DefaultMutableTreeNode(request));
                controllerCount.incrementAndGet();
            });
            root.add(item);
        });

        root.setUserObject(controllerCount.get());
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        model.setRoot(root);
        expandAll(tree, new TreePath(tree.getModel().getRoot()), true);
    }

    @Nullable
    private Request getTreeNodeRequest(@NotNull JTree tree) {
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (sel == null) {
            return null;
        }
        Object object = sel.getUserObject();
        if (!(object instanceof Request)) {
            return null;
        }
        return (Request) object;
    }

    private void expandAll(JTree tree, @NotNull TreePath parent, boolean expand) {
        javax.swing.tree.TreeNode node = (javax.swing.tree.TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                javax.swing.tree.TreeNode n = (javax.swing.tree.TreeNode) e.nextElement();
                TreePath path = parent.pathByAddingChild(n);
                expandAll(tree, path, expand);
            }
        }

        // 展开或收起必须自下而上进行
        if (expand) {
            tree.expandPath(parent);
        } else {
            tree.collapsePath(parent);
        }
    }

    /**
     * 显示右键菜单
     *
     * @param tree    tree
     * @param request request
     * @param x       横坐标
     * @param y       纵坐标
     */
    private void popupMenu(@NotNull JTree tree, @NotNull Request request, int x, int y) {
        JBPopupMenu menu = new JBPopupMenu();
        ActionListener actionListener = actionEvent -> {
            String copy;
            GlobalSearchScope scope = request.getPsiMethod().getResolveScope();
            String contextPath = RestUtil.scanContextPath(project, scope);
            switch (((JMenuItem) actionEvent.getSource()).getMnemonic()) {
                case 0:
                    copy = RestUtil.getRequestUrl(
                            RestUtil.scanListenerProtocol(project, scope),
                            RestUtil.scanListenerPort(project, scope),
                            contextPath,
                            request.getPath()
                    );
                    break;
                case 1:
                    copy = (contextPath == null || "null".equals(contextPath) ? "" : contextPath) +
                            request.getPath();
                    break;
                default:
                    return;
            }
            SystemUtil.setClipboardString(copy);
        };

        // Copy full url
        JMenuItem copyFullUrl = new JMenuItem("Copy full url", AllIcons.Actions.Copy);
        copyFullUrl.setMnemonic(0);
        copyFullUrl.addActionListener(actionListener);
        menu.add(copyFullUrl);

        // Copy api path
        JMenuItem copyApiPath = new JMenuItem("Copy api path", AllIcons.Actions.Copy);
        copyApiPath.setMnemonic(1);
        copyApiPath.addActionListener(actionListener);
        menu.add(copyApiPath);

        menu.show(tree, x, y);
    }
}
