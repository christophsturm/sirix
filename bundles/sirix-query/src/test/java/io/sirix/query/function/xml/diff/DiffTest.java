/*
 * Copyright (c) 2018, Sirix
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.sirix.query.function.xml.diff;

import io.sirix.query.SirixCompileChain;
import io.sirix.query.SirixQueryContext;
import io.sirix.query.node.BasicXmlDBStore;
import io.brackit.query.QueryContext;
import io.brackit.query.QueryException;
import io.brackit.query.Query;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import io.sirix.Holder;
import io.sirix.XmlTestHelper;
import io.sirix.XmlTestHelper.PATHS;
import io.sirix.access.DatabaseConfiguration;
import io.sirix.access.Databases;
import io.sirix.access.ResourceConfiguration;
import io.sirix.api.xml.XmlNodeTrx;
import io.sirix.api.xml.XmlResourceSession;
import io.sirix.exception.SirixException;
import io.sirix.service.xml.shredder.XmlShredder;
import io.sirix.utils.XmlDocumentCreator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * @author Johannes Lichtenberger <a href="mailto:lichtenberger.johannes@gmail.com">mail</a>
 *
 */
public final class DiffTest {
  /** The {@link Holder} instance. */
  private Holder holder;

  @Before
  public void setUp() throws SirixException {
    XmlTestHelper.deleteEverything();
    holder = Holder.generateWtx();
    XmlDocumentCreator.createVersionedWithUpdatesAndDeletes(holder.getXdmNodeWriteTrx());
    holder.getXdmNodeWriteTrx().close();
  }

  @After
  public void tearDown() throws SirixException {
    holder.close();
    XmlTestHelper.closeEverything();
  }

  @Test
  public void testSimpleDiff() throws Exception {
    final Path databasePath = PATHS.PATH2.getFile();

    final DatabaseConfiguration config = new DatabaseConfiguration(databasePath);
    Databases.createXmlDatabase(config);

    try (final var database = Databases.openXmlDatabase(databasePath)) {
      database.createResource(ResourceConfiguration.newBuilder(XmlTestHelper.RESOURCE).build());
      try (final XmlResourceSession manager = database.beginResourceSession(XmlTestHelper.RESOURCE);
           final XmlNodeTrx wtx = manager.beginNodeTrx()) {
        wtx.insertSubtreeAsFirstChild(XmlShredder.createStringReader("<xml>foo<bar/></xml>"));
        wtx.moveTo(3);
        wtx.insertSubtreeAsFirstChild(XmlShredder.createStringReader("<xml>foo<bar/></xml>"));
      }
    }

    // Initialize query context and store.
    try (final BasicXmlDBStore store = BasicXmlDBStore.newBuilder().location(databasePath.getParent()).build()) {
      final QueryContext ctx = SirixQueryContext.createWithNodeStore(store);

      final String dbName = databasePath.getFileName().toString();
      final String resName = XmlTestHelper.RESOURCE;

      final String xq = "xml:diff('" + dbName + "','" + resName + "',1,2)";

      final Query query = new Query(SirixCompileChain.createWithNodeStore(store), xq);

      try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        query.serialize(ctx, new PrintStream(out));
        final String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        out.reset();

        new Query(SirixCompileChain.createWithNodeStore(store), content).execute(ctx);

        final String xq2 = "xml:doc('" + dbName + "','" + resName + "',3)";
        new Query(SirixCompileChain.createWithNodeStore(store), xq2).serialize(ctx, new PrintStream(out));
        final String contentNewRev = new String(out.toByteArray(), StandardCharsets.UTF_8);
        out.reset();

        final String xq3 = "xml:doc('" + dbName + "','" + resName + "',2)";
        new Query(SirixCompileChain.createWithNodeStore(store), xq3).serialize(ctx, new PrintStream(out));
        final String contentOldRev = new String(out.toByteArray(), StandardCharsets.UTF_8);

        Assert.assertEquals(contentNewRev, contentOldRev);

        out.reset();
      }
    }
  }

  @Test
  public void testMultipleDiffs() throws IOException, QueryException {
    final Path database = PATHS.PATH1.getFile();

    // Initialize query context and store.
    try (final BasicXmlDBStore store = BasicXmlDBStore.newBuilder().location(database.getParent()).build()) {
      final QueryContext ctx = SirixQueryContext.createWithNodeStore(store);

      final String dbName = database.toString();
      final String resName = XmlTestHelper.RESOURCE;

      final String xq1 = "xml:diff('" + dbName + "', '" + resName + "',1,5)";

      final Query query = new Query(SirixCompileChain.createWithNodeStore(store), xq1);

      try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        query.serialize(ctx, new PrintStream(out));
        final String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        out.reset();

        new Query(SirixCompileChain.createWithNodeStore(store), content).execute(ctx);

        final String xq2 = "xml:doc('" + dbName + "','" + resName + "',6)";
        new Query(SirixCompileChain.createWithNodeStore(store), xq2).serialize(ctx, new PrintStream(out));
        final String contentNewRev = new String(out.toByteArray(), StandardCharsets.UTF_8);
        out.reset();

        final String xq3 = "xml:doc('" + dbName + "','" + resName + "',5)";
        new Query(SirixCompileChain.createWithNodeStore(store), xq3).serialize(ctx, new PrintStream(out));
        final String contentOldRev = new String(out.toByteArray(), StandardCharsets.UTF_8);

        Assert.assertEquals(contentNewRev, contentOldRev);

        out.reset();
      }
    }
  }
}
