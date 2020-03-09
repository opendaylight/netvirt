/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn.shell;

import com.google.common.base.Preconditions;
import java.net.URI;

import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.impl.codec.DeserializationException;
import org.opendaylight.yangtools.yang.data.impl.codec.TypeDefinitionAwareCodec;
import org.opendaylight.yangtools.yang.data.util.AbstractModuleStringInstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstanceIdentifierCodec extends AbstractModuleStringInstanceIdentifierCodec
    implements SchemaContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceIdentifierCodec.class);

    private DataSchemaContextTree dataSchemaContextTree;
    private SchemaContext context;
    private BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer;

    InstanceIdentifierCodec() {
    }

    public void setBindingNormalizedNodeSerializer(BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer) {
        this.bindingNormalizedNodeSerializer = bindingNormalizedNodeSerializer;
    }

    public void registerSchemaContextListener(SchemaService schemaService) {
        schemaService.registerSchemaContextListener(this);
    }

    @Override
    protected DataSchemaContextTree getDataContextTree() {
        return dataSchemaContextTree;
    }

    @Override
    protected Module moduleForPrefix(final String prefix) {
        return context.findModules(prefix).stream().findFirst().orElse(null);
    }

    @Override
    protected String prefixForNamespace(final URI namespace) {
        final Module module = context.findModules(namespace).stream().findFirst().orElse(null);
        return module == null ? null : module.getName();
    }

    @Override
    public void onGlobalContextUpdated(SchemaContext schemaContext) {
        this.context = schemaContext;
        this.dataSchemaContextTree = DataSchemaContextTree.from(schemaContext);
    }

    public InstanceIdentifier<?> bindingDeserializer(String iidString) throws DeserializationException {
        YangInstanceIdentifier normalizedYangIid = deserialize(iidString);
        return bindingNormalizedNodeSerializer.fromYangInstanceIdentifier(normalizedYangIid);
    }

    public InstanceIdentifier<?> bindingDeserializerOrNull(String iidString) {
        try {
            return bindingDeserializer(iidString);
        } catch (DeserializationException e) {
            LOG.warn("Unable to deserialize iidString", e);
        }
        return null;
    }

    protected Object deserializeKeyValue(DataSchemaNode schemaNode, String value) {
        Preconditions.checkNotNull(schemaNode, "schemaNode cannot be null");
        Preconditions.checkArgument(schemaNode instanceof LeafSchemaNode, "schemaNode must be of type LeafSchemaNode");
        // XMLUtils class is not present so using alternate approach to make it work.
        TypeDefinition<?> type = resolveBaseTypeFrom(((LeafSchemaNode) schemaNode).getType());
        if (type instanceof LeafrefTypeDefinition) {
            type = SchemaContextUtil.getBaseTypeForLeafRef((LeafrefTypeDefinition) type, context, schemaNode);
        }
        final TypeDefinitionAwareCodec<Object, ? extends TypeDefinition<?>> codec =
                TypeDefinitionAwareCodec.from(type);
        Preconditions.checkState(codec != null, String.format("Cannot find codec for type '%s'.", type));
        return codec.deserialize(value);
    }

    private TypeDefinition<?> resolveBaseTypeFrom(final TypeDefinition<?> type) {
        Preconditions.checkNotNull(type, "type cannot be null");
        TypeDefinition<?> superType = type;
        while (superType.getBaseType() != null) {
            superType = superType.getBaseType();
        }
        return superType;
    }

}
