package org.hl7.fhir.dstu2.terminologies;

/*-
 * #%L
 * org.hl7.fhir.dstu2
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.util.List;

import org.hl7.fhir.dstu2.model.UriType;
import org.hl7.fhir.dstu2.model.ValueSet;
import org.hl7.fhir.dstu2.model.ValueSet.ConceptDefinitionComponent;
import org.hl7.fhir.dstu2.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.dstu2.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.dstu2.model.ValueSet.ConceptSetFilterComponent;
import org.hl7.fhir.dstu2.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.dstu2.terminologies.ValueSetExpander.ValueSetExpansionOutcome;
import org.hl7.fhir.dstu2.utils.EOperationOutcome;
import org.hl7.fhir.dstu2.utils.IWorkerContext;
import org.hl7.fhir.dstu2.utils.IWorkerContext.ValidationResult;

public class ValueSetCheckerSimple implements ValueSetChecker {

  private ValueSet valueset;
  private ValueSetExpanderFactory factory;
  private IWorkerContext context;

  public ValueSetCheckerSimple(ValueSet source, ValueSetExpanderFactory factory, IWorkerContext context) {
    this.valueset = source;
    this.factory = factory;
    this.context = context;
  }

  @Override
  public boolean codeInValueSet(String system, String code) throws EOperationOutcome, Exception {
    if (valueset.hasCodeSystem() && system.equals(valueset.getCodeSystem().getSystem()) && codeInDefine(valueset.getCodeSystem().getConcept(), code, valueset.getCodeSystem().getCaseSensitive()))
     return true;

    if (valueset.hasCompose()) {
      boolean ok = false;
      for (UriType uri : valueset.getCompose().getImport()) {
        ok = ok || inImport(uri.getValue(), system, code);
      }
      for (ConceptSetComponent vsi : valueset.getCompose().getInclude()) {
        ok = ok || inComponent(vsi, system, code);
      }
      for (ConceptSetComponent vsi : valueset.getCompose().getExclude()) {
        ok = ok && !inComponent(vsi, system, code);
      }
    }
    
    return false;
  }

  private boolean inImport(String uri, String system, String code) throws EOperationOutcome, Exception {
    ValueSet vs = context.fetchResource(ValueSet.class, uri);
    if (vs == null) 
      return false ; // we can't tell
    return codeInExpansion(factory.getExpander().expand(vs), system, code);
  }

  private boolean codeInExpansion(ValueSetExpansionOutcome vso, String system, String code) throws EOperationOutcome, Exception {
    if (vso.getService() != null) {
      return vso.getService().codeInValueSet(system, code);
    } else {
      for (ValueSetExpansionContainsComponent c : vso.getValueset().getExpansion().getContains()) {
        if (code.equals(c.getCode()) && (system == null || system.equals(c.getSystem())))
          return true;
        if (codeinExpansion(c, system, code)) 
          return true;
      }
    }
    return false;
  }

  private boolean codeinExpansion(ValueSetExpansionContainsComponent cnt, String system, String code) {
    for (ValueSetExpansionContainsComponent c : cnt.getContains()) {
      if (code.equals(c.getCode()) && system.equals(c.getSystem().toString()))
        return true;
      if (codeinExpansion(c, system, code)) 
        return true;
    }
    return false;
  }


  private boolean inComponent(ConceptSetComponent vsi, String system, String code) {
    if (!vsi.getSystem().equals(system))
      return false; 
    // whether we know the system or not, we'll accept the stated codes at face value
    for (ConceptReferenceComponent cc : vsi.getConcept())
      if (cc.getCode().equals(code)) {
        return true;
      }
      
    ValueSet def = context.fetchCodeSystem(system);
    if (def != null) {
      if (!def.getCodeSystem().getCaseSensitive()) {
        // well, ok, it's not case sensitive - we'll check that too now
        for (ConceptReferenceComponent cc : vsi.getConcept())
          if (cc.getCode().equalsIgnoreCase(code)) {
            return false;
          }
      }
      if (vsi.getConcept().isEmpty() && vsi.getFilter().isEmpty()) {
        return codeInDefine(def.getCodeSystem().getConcept(), code, def.getCodeSystem().getCaseSensitive());
      }
      for (ConceptSetFilterComponent f: vsi.getFilter())
        throw new Error("not done yet: "+f.getValue());

      return false;
    } else if (context.supportsSystem(system)) {
      ValidationResult vv = context.validateCode(system, code, null, vsi);
      return vv.isOk();
    } else
      // we don't know this system, and can't resolve it
      return false;
  }

  private boolean codeInDefine(List<ConceptDefinitionComponent> concepts, String code, boolean caseSensitive) {
    for (ConceptDefinitionComponent c : concepts) {
      if (caseSensitive && code.equals(c.getCode()))
        return true;
      if (!caseSensitive && code.equalsIgnoreCase(c.getCode()))
        return true;
      if (codeInDefine(c.getConcept(), code, caseSensitive))
        return true;
    }
    return false;
  }

}