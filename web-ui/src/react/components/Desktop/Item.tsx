import * as React from 'react';
import {Paper} from '@mui/material';

const sx = {
  height: '100%',
  width: '100%',
};

const Item: React.FC<{children: React.ReactNode}> = ({children}): React.ReactElement => {
  return <Paper sx={sx}>{children}</Paper>;
};

export default Item;
