import {AppBar, Box, Button, IconButton, SxProps, Toolbar, Typography} from '@mui/material';
import {DataGrid, GridColDef, GridRowsProp} from '@mui/x-data-grid';
import MenuIcon from '@mui/icons-material/Menu';
import React from 'react';

const rows: GridRowsProp = [
  {id: 1, col1: 'Hello', col2: 'World'},
  {id: 2, col1: 'DataGridPro', col2: 'is Awesome'},
  {id: 3, col1: 'MUI', col2: 'is Amazing'},
];

const columns: GridColDef[] = [
  {field: 'id', headerName: 'Name', width: 150},
  {field: 'status', headerName: 'Status', width: 150},
  {field: 'subjects', headerName: 'Subjects', flex: 1},
];

const boxSx: SxProps = {display: 'flex', height: '100%', flexDirection: 'column'};
const VotingOwner: React.FC = (): React.JSX.Element => {
  return <Box sx={boxSx}>
    <AppBar position="static">
      <Toolbar>
        <IconButton
          size="large"
          edge="start"
          color="inherit"
          aria-label="menu"
          sx={{mr: 2}}
        >
          <MenuIcon/>
        </IconButton>
        <Typography variant="h6" component="div" sx={{flexGrow: 1}}>
          You Voting
        </Typography>
        <Button color="inherit">Logout</Button>
      </Toolbar>
    </AppBar>
    <DataGrid sx={{flexGrow: 1}} columns={columns} rows={rows} /*checkboxSelection*//>
  </Box>;
};

export default VotingOwner;
