package ca.uhn.fhir.cr.common;

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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.context.support.ValueSetExpansionOptions;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IBaseResource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PreExpandedValidationSupport implements IValidationSupport {
	private FhirContext fhirContext;

	public PreExpandedValidationSupport(FhirContext fhirContext) {
		this.fhirContext = fhirContext;
	}

	@Override
	public ValueSetExpansionOutcome expandValueSet(ValidationSupportContext theValidationSupportContext,
			@Nullable ValueSetExpansionOptions theExpansionOptions, @Nonnull IBaseResource theValueSetToExpand) {
		Validate.notNull(theValueSetToExpand, "theValueSetToExpand must not be null or blank");

		if (!getFhirContext().getResourceDefinition("ValueSet").getChildByName("expansion").getAccessor()
				.getValues(theValueSetToExpand).isEmpty()) {
			return new ValueSetExpansionOutcome(theValueSetToExpand);
		} else {
			return IValidationSupport.super.expandValueSet(theValidationSupportContext, theExpansionOptions,
					theValueSetToExpand);
		}
	}

	@Override
	public FhirContext getFhirContext() {
		return this.fhirContext;
	}

}
