/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nlp;

import java.io.OutputStream;

import neo4japi.domain.Document;
import opennlp.tools.cmdline.EvaluationErrorPrinter;
import opennlp.tools.doccat.DoccatEvaluationMonitor;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.util.eval.EvaluationMonitor;

/**
 * A default implementation of {@link EvaluationMonitor} that prints to an
 * output stream.
 *
 */
public class DoccatEvaluationErrorListener extends
        EvaluationErrorPrinter<DocumentSample> implements DoccatEvaluationMonitor {

    /**
     * Creates a listener that will print to System.err
     */
    public DoccatEvaluationErrorListener() {
        super(System.err);
    }

    /**
     * Creates a listener that will print to a given {@link OutputStream}
     */
    public DoccatEvaluationErrorListener(OutputStream outputStream) {
        super(outputStream);
    }

    @Override
    public void missclassified(DocumentSample reference, DocumentSample prediction) {
        DocumentSampleWithFilename ref = (DocumentSampleWithFilename)reference;
        DocumentSampleWithFilename pred = (DocumentSampleWithFilename)prediction;
        String details = "\"" + ref.getFilename() + "\": {\"Expected\": \"" + ref.getCategory() + "\", \"Predicted\": \"" + pred.getCategory() + "\"},";
        printStream.println(details);
        printStream.println();
    }

}

