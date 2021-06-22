/*
 * CloudBeaver - Cloud Database Manager
 * Copyright (C) 2020-2021 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0.
 * you may not use this file except in compliance with the License.
 */

import path from 'path';
import type { SVGAttributes } from 'react';

import { GlobalConstants } from '@cloudbeaver/core-utils';

export const Icon: React.FC<SVGAttributes<any>> = function Icon(props) {
  const { name = '', className, ...rest } = props;
  const cn = `icon ${className || ''}`;
  const url = name.startsWith('/') ? path.join(GlobalConstants.rootURI, name) : `icons/icons.svg#${name}`;
  return (
    <svg {...rest} className={cn} aria-hidden="true" focusable="false">
      <use href={url} />
    </svg>
  );
};