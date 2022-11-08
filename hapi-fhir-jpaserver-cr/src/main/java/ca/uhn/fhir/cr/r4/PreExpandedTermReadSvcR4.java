package ca.uhn.fhir.cr.r4;

/*-
 * #%L
 * HAPI FHIR JPA Server - Clinical Reasoning
 * %%
 * Copyright (C) 2014 - 2022 Smile CDR, Inc.
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

import ca.uhn.fhir.context.support.ValueSetExpansionOptions;
import ca.uhn.fhir.jpa.term.TermReadSvcImpl;
import org.hl7.fhir.r4.model.ValueSet;

public class PreExpandedTermReadSvcR4 extends TermReadSvcImpl {

	@Override
	public ValueSet expandValueSet(ValueSetExpansionOptions theExpansionOptions, ValueSet theValueSetToExpand) {
		if (theValueSetToExpand.hasExpansion()) {
			return theValueSetToExpand;
		}

		return super.expandValueSet(theExpansionOptions, theValueSetToExpand);
	}
}
