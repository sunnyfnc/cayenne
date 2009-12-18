/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/

package org.apache.cayenne.modeler;

import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;

import org.apache.cayenne.access.DataDomain;
import org.apache.cayenne.access.DataNode;
import org.apache.cayenne.map.DataMap;
import org.apache.cayenne.project.Project;
import org.apache.cayenne.project.ProjectPath;
import org.apache.cayenne.project.ProjectTraversal;
import org.apache.cayenne.project.ProjectTraversalHandler;

/**
 * ProjectTreeModel is a model of Cayenne project tree.
 */
public class ProjectTreeModel extends DefaultTreeModel {

    /**
     * Creates a tree of Swing TreeNodes wrapping Cayenne project object. Returns the root
     * node of the tree.
     * 
     * @deprecated since 3.1 use {@link ProjectTreeFactory}.
     */
    public static DefaultMutableTreeNode wrapProjectNode(Object node) {
        TraversalHelper helper = new TraversalHelper();
        new ProjectTraversal(helper, true).traverse(node);
        return helper.getStartNode();
    }

    /**
     * Constructor for ProjectTreeModel.
     */
    public ProjectTreeModel(Project project) {
        super(wrapProjectNode(project));
    }

    /**
     * Re-inserts a tree node to preserve the correct ordering of items. Assumes that the
     * tree is already ordered, except for one node.
     */
    public void positionNode(
            MutableTreeNode parent,
            DefaultMutableTreeNode treeNode,
            Comparator comparator) {

        if (treeNode == null) {
            return;
        }

        if (parent == null && treeNode != getRoot()) {
            parent = (MutableTreeNode) treeNode.getParent();
            if (parent == null) {
                parent = getRootNode();
            }
        }

        Object object = treeNode.getUserObject();

        int len = parent.getChildCount();
        int ins = -1;
        int rm = -1;

        for (int i = 0; i < len; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) parent.getChildAt(i);

            // remember to remove node
            if (node == treeNode) {
                rm = i;
                continue;
            }

            // no more insert checks
            if (ins >= 0) {
                continue;
            }

            // ObjEntities go before DbEntities
            if (comparator.compare(object, node.getUserObject()) <= 0) {
                ins = i;
            }
        }

        if (ins < 0) {
            ins = len;
        }

        if (rm == ins) {
            return;
        }

        // remove
        if (rm >= 0) {
            removeNodeFromParent(treeNode);
            if (rm < ins) {
                ins--;
            }
        }

        // insert
        insertNodeInto(treeNode, parent, ins);
    }

    /**
     * Returns root node cast into DefaultMutableTreeNode.
     */
    public DefaultMutableTreeNode getRootNode() {
        return (DefaultMutableTreeNode) super.getRoot();
    }

    public DefaultMutableTreeNode getNodeForObjectPath(Object[] path) {
        if (path == null || path.length == 0) {
            return null;
        }

        DefaultMutableTreeNode currentNode = getRootNode();

        // adjust for root node being in the path
        int start = 0;
        if (currentNode.getUserObject() == path[0]) {
            start = 1;
        }

        for (int i = start; i < path.length; i++) {
            DefaultMutableTreeNode foundNode = null;
            Enumeration children = currentNode.children();
            while (children.hasMoreElements()) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) children
                        .nextElement();
                if (child.getUserObject() == path[i]) {
                    foundNode = child;
                    break;
                }
            }

            if (foundNode == null) {
                return null;
            }
            else {
                currentNode = foundNode;
            }
        }

        return currentNode;
    }

    static class TraversalHelper implements ProjectTraversalHandler {

        protected DefaultMutableTreeNode startNode;
        protected Map<Object, DefaultMutableTreeNode> nodesMap;

        public TraversalHelper() {
            this.nodesMap = new HashMap<Object, DefaultMutableTreeNode>();
        }

        public DefaultMutableTreeNode getStartNode() {
            return startNode;
        }

        public void registerNode(DefaultMutableTreeNode node) {
            nodesMap.put(node.getUserObject(), node);
        }

        public void projectNode(ProjectPath nodePath) {

            Object parent = nodePath.getObjectParent();
            Object object = nodePath.getObject();
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(object);

            if (startNode == null) {
                startNode = node;
            }
            else {
                DefaultMutableTreeNode nodeParent = nodesMap.get(parent);
                nodeParent.add(node);
            }

            registerNode(node);
        }

        public boolean shouldReadChildren(Object node, ProjectPath parentPath) {
            // do not read deatils of linked maps
            if ((node instanceof DataMap)
                    && parentPath != null
                    && (parentPath.getObject() instanceof DataNode)) {
                return false;
            }

            return (node instanceof Project)
                    || (node instanceof DataDomain)
                    || (node instanceof DataMap)
                    || (node instanceof DataNode);
        }
    }

}
