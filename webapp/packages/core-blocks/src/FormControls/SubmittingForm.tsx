/*
 * CloudBeaver - Cloud Database Manager
 * Copyright (C) 2020-2021 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0.
 * you may not use this file except in compliance with the License.
 */

import { forwardRef, useContext } from 'react';

import { Executor } from '@cloudbeaver/core-executor';

import { useObjectRef } from '../useObjectRef';
import { FormContext, IChangeData, IFormContext } from './FormContext';

type FormDetailedProps = Omit<React.DetailedHTMLProps<React.FormHTMLAttributes<HTMLFormElement>, HTMLFormElement>, 'onChange'> & {
  disabled?: boolean;
  onChange?: (value: string | number | boolean | null | undefined, name: string | undefined) => void;
};

export const SubmittingForm = forwardRef<HTMLFormElement, FormDetailedProps>(function SubmittingForm(
  {
    disabled,
    children,
    onSubmit,
    onChange = () => {},
    ...rest
  },
  ref
) {
  const props = useObjectRef(() => ({
    handleSubmit(e: React.FormEvent<HTMLFormElement>) {
      e.preventDefault();
      this.onSubmit?.(e);
    },
  }), {
    parentContext: useContext(FormContext),
    onChange,
    onSubmit,
  }, ['handleSubmit']);

  const context = useObjectRef<IFormContext>(() => ({
    changeExecutor: new Executor<IChangeData>(),
    change(value, name) {
      props.onChange(value, name);
      props.parentContext?.change(value, name);
      this.changeExecutor.execute({ value, name });
    },
  }), false, ['change']);

  return (
    <form {...rest} ref={ref} onSubmit={props.handleSubmit}>
      <fieldset disabled={disabled} className={rest.className}>
        <FormContext.Provider value={context}>
          {children}
        </FormContext.Provider>
      </fieldset>
      <button type="submit" hidden />
    </form>
  );
});
