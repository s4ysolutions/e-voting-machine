import React from 'react';
// eslint-disable-next-line sort-imports
import CssBaseline from '@mui/material/ScopedCssBaseline';
import Desktop from './react/components/Desktop';
import {createRoot} from 'react-dom/client';

const container = document.getElementById('reactMount');
const root = createRoot(container);
root.render(<CssBaseline enableColorScheme><Desktop/></CssBaseline>);
